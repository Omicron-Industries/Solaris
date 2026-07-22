"use strict";

/*
 * Solaris Web Map — a static, fully client-side viewer for the ".solmap" files produced by
 * Solaris's in-game "Export for Web Map" button. Nothing here ever leaves the browser: files
 * are read locally via the File API, decompressed with the browser's own DecompressionStream,
 * and rendered straight to a <canvas>. No server, no upload, no build step.
 *
 * .solmap binary layout (big-endian throughout, gzip-wrapped — see
 * SolarisWebExporter#writeSolmap on the Java side for the authoritative source of truth):
 *   magic   int32  = 0x534F4C4D ("SOLM")
 *   version int32  = 1
 *   worldName  UTF (2-byte length prefix + UTF-8 bytes, Java DataOutputStream#writeUTF style)
 *   dimension  UTF
 *   exportedAtEpochMillis int64
 *   chunkCount int32
 *   minChunkX, minChunkZ, maxChunkX, maxChunkZ  int32 each
 *   per chunk (chunkCount times):
 *     chunkX, chunkZ  int32 each
 *     256 x [r, g, b]  (one byte each, row-major z*16+x — no alpha, always opaque)
 *     256 x height     (int16 each, same row-major order)
 */

const MAGIC = 0x534f4c4d;
const SUPPORTED_VERSION = 1;

const state = {
  map: null, // { worldName, dimension, exportedAt, minChunkX, minChunkZ, width, height, canvas, baseImageData, heights }
  waypoints: [], // filtered to the loaded map's dimension
  view: { offsetX: 0, offsetY: 0, zoom: 1, minZoom: 0.05, maxZoom: 32 },
  dragging: false,
  lastPointer: null,
  options: { hillshading: true, chunkGrid: false },
};

const canvas = document.getElementById("map-canvas");
const ctx = canvas.getContext("2d");
const emptyState = document.getElementById("empty-state");
const tooltip = document.getElementById("tooltip");
const coordsEl = document.getElementById("coords");
const metaEl = document.getElementById("meta");
const optionsEl = document.getElementById("options");
const scalebarEl = document.getElementById("scalebar");
const scalebarLineEl = document.getElementById("scalebar-line");
const scalebarLabelEl = document.getElementById("scalebar-label");

document.getElementById("opt-hillshade").addEventListener("change", (e) => {
  state.options.hillshading = e.target.checked;
  if (state.map) {
    rebuildCanvas(state.map);
    render();
  }
});
document.getElementById("opt-grid").addEventListener("change", (e) => {
  state.options.chunkGrid = e.target.checked;
  render();
});

// ── File loading ─────────────────────────────────────────────────────────────

document.getElementById("map-input").addEventListener("change", (e) => {
  if (e.target.files[0]) loadMapFile(e.target.files[0]);
});
document.getElementById("waypoints-input").addEventListener("change", (e) => {
  if (e.target.files[0]) loadWaypointsFile(e.target.files[0]);
});

["dragenter", "dragover"].forEach((evt) =>
  document.body.addEventListener(evt, (e) => e.preventDefault()));
document.body.addEventListener("drop", (e) => {
  e.preventDefault();
  const files = Array.from(e.dataTransfer.files || []);
  const map = files.find((f) => f.name.endsWith(".solmap"));
  const waypoints = files.find((f) => f.name.endsWith(".json"));
  if (map) loadMapFile(map);
  if (waypoints) loadWaypointsFile(waypoints);
});

async function loadMapFile(file) {
  try {
    const buf = await file.arrayBuffer();
    const parsed = await parseSolmap(buf);
    state.map = parsed;
    state.waypoints = []; // stale until re-matched against the new dimension
    rebuildCanvas(parsed);
    fitToView();
    updateMeta();
    emptyState.classList.add("hidden");
    canvas.classList.remove("hidden");
    optionsEl.classList.remove("hidden");
    scalebarEl.classList.remove("hidden");
    render();
  } catch (err) {
    alert("Couldn't load that .solmap file: " + err.message);
  }
}

async function loadWaypointsFile(file) {
  try {
    const text = await file.text();
    const data = JSON.parse(text);
    const all = Array.isArray(data) ? data : data.waypoints || [];
    state.waypoints = state.map ?
        all.filter((w) => w.visible !== false && w.dimension === state.map.dimension) :
        all.filter((w) => w.visible !== false);
    updateMeta();
    render();
  } catch (err) {
    alert("Couldn't load that waypoints file: " + err.message);
  }
}

// ── .solmap parsing ──────────────────────────────────────────────────────────

async function parseSolmap(arrayBuffer) {
  if (typeof DecompressionStream === "undefined") {
    throw new Error("Your browser doesn't support DecompressionStream — try a current " +
        "Chrome, Edge, Firefox, or Safari.");
  }
  const decompressed = await new Response(
      new Blob([arrayBuffer]).stream().pipeThrough(new DecompressionStream("gzip"))).arrayBuffer();

  const view = new DataView(decompressed);
  const bytes = new Uint8Array(decompressed);
  let offset = 0;
  const i32 = () => { const v = view.getInt32(offset, false); offset += 4; return v; };
  const i16 = () => { const v = view.getInt16(offset, false); offset += 2; return v; };
  const i64 = () => { const v = view.getBigInt64(offset, false); offset += 8; return v; };
  const u8 = () => bytes[offset++];
  const utf = () => {
    const len = view.getUint16(offset, false);
    offset += 2;
    const str = new TextDecoder("utf-8").decode(bytes.subarray(offset, offset + len));
    offset += len;
    return str;
  };

  if (i32() !== MAGIC) throw new Error("Not a Solaris .solmap file (bad magic number).");
  const version = i32();
  if (version !== SUPPORTED_VERSION) throw new Error("Unsupported .solmap version " + version + ".");

  const worldName = utf();
  const dimension = utf();
  const exportedAt = i64();
  const chunkCount = i32();
  const minChunkX = i32();
  const minChunkZ = i32();
  const maxChunkX = i32();
  const maxChunkZ = i32();

  const width = (maxChunkX - minChunkX + 1) * 16;
  const height = (maxChunkZ - minChunkZ + 1) * 16;
  if (width <= 0 || height <= 0 || width > 32000 || height > 32000) {
    throw new Error("Map bounds are too large to render in a single canvas (" + width + "x" +
        height + " px) — this is a known v1 limitation for extremely large explored areas.");
  }

  const imageData = new ImageData(width, height);
  const pixels = imageData.data;
  const heights = new Int16Array(width * height);
  const hasData = new Uint8Array(width * height);

  for (let c = 0; c < chunkCount; c++) {
    const cx = i32();
    const cz = i32();
    const baseX = (cx - minChunkX) * 16;
    const baseZ = (cz - minChunkZ) * 16;
    for (let p = 0; p < 256; p++) {
      const r = u8(), g = u8(), b = u8();
      const localX = p % 16, localZ = (p / 16) | 0;
      const pixelIdx = (baseZ + localZ) * width + (baseX + localX);
      const idx = pixelIdx * 4;
      pixels[idx] = r;
      pixels[idx + 1] = g;
      pixels[idx + 2] = b;
      pixels[idx + 3] = 255;
      hasData[pixelIdx] = 1;
    }
    for (let p = 0; p < 256; p++) {
      const localX = p % 16, localZ = (p / 16) | 0;
      heights[(baseZ + localZ) * width + (baseX + localX)] = i16();
    }
  }

  return { worldName, dimension, exportedAt, minChunkX, minChunkZ, maxChunkX, maxChunkZ,
    width, height, baseImageData: imageData, heights, hasData, canvas: null };
}

// Hillshading mirrors the in-game SolarisTexture#applyHillshading formula so the web map reads
// as the same place, not a flatter re-derivation of it.
const LIGHT_X = -0.5, LIGHT_Y = -0.5, LIGHT_Z = 0.8;
const LIGHT_LEN = Math.sqrt(LIGHT_X * LIGHT_X + LIGHT_Y * LIGHT_Y + LIGHT_Z * LIGHT_Z);
const FLAT_SHADE = LIGHT_Z / LIGHT_LEN;
const HILLSHADE_GAIN = 1.8;

function hillshadeFactor(dzdx, dzdy) {
  const nx = -dzdx, ny = -dzdy, nz = 1;
  const nLen = Math.sqrt(nx * nx + ny * ny + nz * nz);
  const shade = (nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z) / (nLen * LIGHT_LEN);
  const factor = 1 + HILLSHADE_GAIN * (shade - FLAT_SHADE);
  return Math.max(0.3, Math.min(1.8, factor));
}

function rebuildCanvas(map) {
  const { width, height, baseImageData, heights, hasData } = map;
  const off = document.createElement("canvas");
  off.width = width;
  off.height = height;
  const offCtx = off.getContext("2d");

  if (!state.options.hillshading) {
    offCtx.putImageData(baseImageData, 0, 0);
    map.canvas = off;
    return;
  }

  const shaded = new ImageData(new Uint8ClampedArray(baseImageData.data), width, height);
  const pixels = shaded.data;
  for (let z = 0; z < height; z++) {
    for (let x = 0; x < width; x++) {
      const idx = z * width + x;
      if (!hasData[idx]) continue;
      const west = x > 0 ? heights[idx - 1] : heights[idx];
      const east = x < width - 1 ? heights[idx + 1] : heights[idx];
      const north = z > 0 ? heights[idx - width] : heights[idx];
      const south = z < height - 1 ? heights[idx + width] : heights[idx];
      const factor = hillshadeFactor((east - west) * 0.5, (south - north) * 0.5);
      if (factor === 1) continue;
      const p = idx * 4;
      pixels[p] = Math.max(0, Math.min(255, pixels[p] * factor));
      pixels[p + 1] = Math.max(0, Math.min(255, pixels[p + 1] * factor));
      pixels[p + 2] = Math.max(0, Math.min(255, pixels[p + 2] * factor));
    }
  }
  offCtx.putImageData(shaded, 0, 0);
  map.canvas = off;
}

// ── View / rendering ─────────────────────────────────────────────────────────

function resizeCanvas() {
  const rect = canvas.parentElement.getBoundingClientRect();
  canvas.width = rect.width;
  canvas.height = rect.height;
}

function fitToView() {
  resizeCanvas();
  const m = state.map;
  const zoom = Math.min(canvas.width / m.width, canvas.height / m.height) * 0.9;
  state.view.zoom = Math.max(0.001, zoom);
  state.view.minZoom = state.view.zoom / 8;
  state.view.maxZoom = 32;
  state.view.offsetX = (canvas.width - m.width * state.view.zoom) / 2;
  state.view.offsetY = (canvas.height - m.height * state.view.zoom) / 2;
}

function worldToScreen(worldX, worldZ) {
  const m = state.map;
  const px = worldX - m.minChunkX * 16;
  const pz = worldZ - m.minChunkZ * 16;
  return [state.view.offsetX + px * state.view.zoom, state.view.offsetY + pz * state.view.zoom];
}

function screenToWorld(sx, sy) {
  const m = state.map;
  const px = (sx - state.view.offsetX) / state.view.zoom;
  const pz = (sy - state.view.offsetY) / state.view.zoom;
  return [Math.floor(px + m.minChunkX * 16), Math.floor(pz + m.minChunkZ * 16)];
}

function render() {
  if (!state.map) return;
  ctx.imageSmoothingEnabled = false;
  ctx.fillStyle = "#0a0b0d";
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  const v = state.view;
  ctx.drawImage(state.map.canvas, v.offsetX, v.offsetY, state.map.width * v.zoom,
      state.map.height * v.zoom);

  if (state.options.chunkGrid) drawChunkGrid();

  for (const w of state.waypoints) {
    const [sx, sy] = worldToScreen(w.x, w.z);
    if (sx < -20 || sy < -20 || sx > canvas.width + 20 || sy > canvas.height + 20) continue;
    ctx.beginPath();
    ctx.arc(sx, sy, 5, 0, Math.PI * 2);
    ctx.fillStyle = waypointColor(w);
    ctx.fill();
    ctx.lineWidth = 1.5;
    ctx.strokeStyle = "#000000";
    ctx.stroke();
  }

  updateScaleBar();
}

function drawChunkGrid() {
  const v = state.view;
  const step = 16 * v.zoom;
  if (step < 4) return; // too dense to be useful, and expensive to draw
  ctx.strokeStyle = "rgba(255, 255, 255, 0.15)";
  ctx.lineWidth = 1;
  ctx.beginPath();
  const startX = v.offsetX % step;
  for (let x = startX; x < canvas.width; x += step) {
    ctx.moveTo(Math.round(x) + 0.5, 0);
    ctx.lineTo(Math.round(x) + 0.5, canvas.height);
  }
  const startY = v.offsetY % step;
  for (let y = startY; y < canvas.height; y += step) {
    ctx.moveTo(0, Math.round(y) + 0.5);
    ctx.lineTo(canvas.width, Math.round(y) + 0.5);
  }
  ctx.stroke();
}

const SCALE_STEPS = [5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000];

function updateScaleBar() {
  const zoom = state.view.zoom;
  let blocks = SCALE_STEPS[0];
  for (const step of SCALE_STEPS) {
    if (step * zoom > 150) break;
    blocks = step;
  }
  scalebarLineEl.style.width = Math.round(blocks * zoom) + "px";
  scalebarLabelEl.textContent = blocks + " blocks";
}

function waypointColor(w) {
  if (typeof w.color === "string" && /^#?[0-9a-fA-F]{6}$/.test(w.color)) {
    return w.color.startsWith("#") ? w.color : "#" + w.color;
  }
  return "#ffffff";
}

// ── Interaction: pan, zoom, hover ────────────────────────────────────────────

canvas.addEventListener("pointerdown", (e) => {
  if (!state.map) return;
  state.dragging = true;
  state.lastPointer = [e.clientX, e.clientY];
  canvas.classList.add("dragging");
  canvas.setPointerCapture(e.pointerId);
});

canvas.addEventListener("pointerup", (e) => {
  state.dragging = false;
  canvas.classList.remove("dragging");
  canvas.releasePointerCapture(e.pointerId);
});

canvas.addEventListener("pointermove", (e) => {
  if (!state.map) return;
  const rect = canvas.getBoundingClientRect();
  const sx = e.clientX - rect.left;
  const sy = e.clientY - rect.top;

  if (state.dragging && state.lastPointer) {
    state.view.offsetX += e.clientX - state.lastPointer[0];
    state.view.offsetY += e.clientY - state.lastPointer[1];
    state.lastPointer = [e.clientX, e.clientY];
    render();
  }

  const [wx, wz] = screenToWorld(sx, sy);
  coordsEl.textContent = "X: " + wx + "  Z: " + wz;
  coordsEl.classList.remove("hidden");

  const hovered = hitTestWaypoint(sx, sy);
  if (hovered) {
    tooltip.textContent = hovered.name || "Waypoint";
    tooltip.style.left = sx + "px";
    tooltip.style.top = sy + "px";
    tooltip.classList.remove("hidden");
  } else {
    tooltip.classList.add("hidden");
  }
});

canvas.addEventListener("pointerleave", () => {
  coordsEl.classList.add("hidden");
  tooltip.classList.add("hidden");
});

canvas.addEventListener("wheel", (e) => {
  if (!state.map) return;
  e.preventDefault();
  const rect = canvas.getBoundingClientRect();
  const sx = e.clientX - rect.left;
  const sy = e.clientY - rect.top;
  const v = state.view;

  const worldPxX = (sx - v.offsetX) / v.zoom;
  const worldPxZ = (sy - v.offsetY) / v.zoom;

  const factor = e.deltaY < 0 ? 1.2 : 1 / 1.2;
  v.zoom = Math.max(v.minZoom, Math.min(v.maxZoom, v.zoom * factor));

  v.offsetX = sx - worldPxX * v.zoom;
  v.offsetY = sy - worldPxZ * v.zoom;
  render();
}, { passive: false });

function hitTestWaypoint(sx, sy) {
  for (const w of state.waypoints) {
    const [wsx, wsy] = worldToScreen(w.x, w.z);
    if (Math.hypot(sx - wsx, sy - wsy) <= 7) return w;
  }
  return null;
}

window.addEventListener("resize", () => {
  if (!state.map) return;
  resizeCanvas();
  render();
});

// ── Meta bar ──────────────────────────────────────────────────────────────────

function updateMeta() {
  if (!state.map) return;
  document.getElementById("meta-world").textContent = "World: " + state.map.worldName;
  document.getElementById("meta-dim").textContent = "Dimension: " + state.map.dimension;
  const date = new Date(Number(state.map.exportedAt));
  document.getElementById("meta-date").textContent = "Exported: " + date.toLocaleString();
  document.getElementById("meta-waypoints").textContent =
      state.waypoints.length > 0 ? state.waypoints.length + " waypoints" : "";
  metaEl.classList.remove("hidden");
}
