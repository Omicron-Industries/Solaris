package net.phoenixvine.solaris.api;

import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.solaris.client.overlay.SolarisOverlay;
import net.phoenixvine.solaris.client.overlay.SolarisOverlayRegistry;
import net.phoenixvine.solaris.client.render.SolarisTexture;
import net.phoenixvine.solaris.client.waypoint.Waypoint;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;

import java.util.List;

/**
 * Stable public API for Solaris. Mirrors the {@code GuildAPI}/{@code QuestAPI}/
 * {@code DomainAPI} house style: a final class, private constructor, static methods.
 *
 * Client-side only — Solaris has no server component. Safe to call from any client-side
 * code; a no-op guard isn't needed here the way it is for the server-side APIs, since
 * the overlay registry and texture cache are themselves plain client-side statics.
 */
public final class SolarisAPI {

    private SolarisAPI() {}

    /** Registers an overlay layer drawn on top of Solaris's terrain base layer. */
    public static void registerOverlay(SolarisOverlay overlay) {
        SolarisOverlayRegistry.register(overlay);
        requestRefresh();
    }

    public static void unregisterOverlay(SolarisOverlay overlay) {
        SolarisOverlayRegistry.unregister(overlay);
        requestRefresh();
    }

    /**
     * Forces every currently-open Solaris map/minimap to redraw on its next frame — call
     * after data an overlay depends on changes (e.g. Phoenix Domains calling this right
     * after a claim/unclaim), rather than waiting for the player to cross a chunk boundary.
     */
    public static void requestRefresh() {
        SolarisTexture.invalidateAll();
    }

    // ── Waypoints ────────────────────────────────────────────────────────────

    /** Adds a waypoint (persisted per-world) and returns it, so the caller can keep a reference. */
    public static Waypoint addWaypoint(String name, ResourceLocation dimension, int x, int y, int z, String colorHex) {
        Waypoint w = new Waypoint(name, dimension, x, y, z, colorHex);
        WaypointManager.add(w);
        return w;
    }

    public static void removeWaypoint(String id) {
        WaypointManager.remove(id);
    }

    public static List<Waypoint> getWaypoints() {
        return WaypointManager.getAll();
    }
}
