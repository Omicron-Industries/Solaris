package net.phoenixvine.solaris.client.render;

/**
 * Pan/zoom math ported from PhoenixChronicles' {@code CanvasViewport} — cursor-anchored
 * zoom (the point under the mouse stays fixed while zoom changes) plus screen↔world pixel
 * coordinate mapping, generalized from that class's "sidebar/header" GUI origin to a plain
 * screen-space origin since Solaris's map has no fixed side panel.
 */
public class MapViewport {

    private static final float ZOOM_STEP = 0.1f;

    private float zoomMin;
    private float zoomMax;

    private double offsetX;
    private double offsetY;
    private float zoom = 1.0f;

    public MapViewport() {
        this(0.25f, 4.0f);
    }

    public MapViewport(float zoomMin, float zoomMax) {
        this.zoomMin = zoomMin;
        this.zoomMax = zoomMax;
    }

    public void pan(double dx, double dy) {
        this.offsetX += dx;
        this.offsetY += dy;
    }

    /** Adjusts zoom, keeping the world point under (mouseX, mouseY) stationary on screen. */
    public boolean adjustZoomToAnchor(double scrollDelta, double mouseX, double mouseY, int originX, int originY) {
        float oldZoom = this.zoom;
        this.zoom = Math.max(zoomMin, Math.min(zoomMax, this.zoom + (float) scrollDelta * ZOOM_STEP));
        if (this.zoom == oldZoom) return false;

        float ratio = this.zoom / oldZoom;
        double localX = mouseX - originX;
        double localY = mouseY - originY;
        this.offsetX = localX - (localX - this.offsetX) * ratio;
        this.offsetY = localY - (localY - this.offsetY) * ratio;
        return true;
    }

    public double toWorldX(double screenX, int originX) {
        return (screenX - originX - offsetX) / zoom;
    }

    public double toWorldZ(double screenY, int originY) {
        return (screenY - originY - offsetY) / zoom;
    }

    public double toScreenX(double worldX, int originX) {
        return worldX * zoom + originX + offsetX;
    }

    public double toScreenY(double worldZ, int originY) {
        return worldZ * zoom + originY + offsetY;
    }

    public void setOffset(double x, double y) {
        this.offsetX = x;
        this.offsetY = y;
    }

    /**
     * Raises the minimum zoom (never lowers it below the value passed to the constructor),
     * clamping the current zoom up to match if it's now below the new floor. Used to prevent
     * the map content from ever rendering smaller than the viewport that frames it.
     *
     * On a small map radius / large window, the zoom needed just to make the content cover
     * the frame can end up above {@code zoomMax} — when that happened, {@code zoomMin} sat
     * above {@code zoomMax} permanently, and {@link #adjustZoomToAnchor}'s
     * {@code max(zoomMin, min(zoomMax, ...))} clamp always resolved to the same fixed value
     * no matter which way you scrolled, i.e. scrolling did nothing at all ("can't scroll when
     * fully zoomed out"). Raising {@code zoomMax} to match (with some headroom to actually
     * zoom in from there) keeps the range valid instead of collapsing it to a single point.
     */
    public void raiseZoomMin(float newZoomMin) {
        if (newZoomMin > zoomMin) zoomMin = newZoomMin;
        if (zoom < zoomMin) zoom = zoomMin;
        if (zoomMin > zoomMax) zoomMax = zoomMin * 2f;
    }

    /**
     * Clamps the pan offset so the rendered {@code contentSize x contentSize} square always
     * fully covers the given screen-space frame — otherwise, zooming out near a frame edge
     * (cursor-anchored zoom shifts the offset toward wherever the cursor was) can slide the
     * content out from under the frame even though {@link #raiseZoomMin} already guarantees
     * it's never smaller than the frame. Content bigger than the frame at the current zoom is
     * simply pinned to the nearest edge instead of leaving a gap; content that somehow ends up
     * smaller (zoom floor not yet applied) is centered instead of pinned to avoid an
     * inverted/degenerate clamp range.
     */
    public void clampOffsetToCover(double contentSize, int frameX, int frameW, int frameY, int frameH) {
        double renderedW = contentSize * zoom;
        double minX = frameX + frameW - renderedW;
        double maxX = frameX;
        offsetX = minX > maxX ? (minX + maxX) / 2.0 : clampD(offsetX, minX, maxX);

        double renderedH = contentSize * zoom;
        double minY = frameY + frameH - renderedH;
        double maxY = frameY;
        offsetY = minY > maxY ? (minY + maxY) / 2.0 : clampD(offsetY, minY, maxY);
    }

    private static double clampD(double v, double min, double max) {
        return Math.min(Math.max(v, min), max);
    }

    public double getOffsetX() {
        return offsetX;
    }

    public double getOffsetY() {
        return offsetY;
    }

    public float getZoom() {
        return zoom;
    }
}
