package net.phoenixvine.solaris.integration.gtceu;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.phoenixvine.solaris.client.waypoint.Waypoint;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;

/**
 * Solaris's implementation of GTCEu's own map-integration hook,
 * {@code com.gregtechceu.gtceu.integration.map.IWaypointHandler} — lets clicking a vein in
 * the prospector scanner's own in-item map pin a Solaris waypoint at it directly, the same
 * way it already works for Xaero/JourneyMap/FTB Chunks. Registered (and force-activated, see
 * {@link GtceuIntegration#init()}) once GTCEu is detected.
 */
public class SolarisWaypointHandler implements com.gregtechceu.gtceu.integration.map.IWaypointHandler {

    @Override
    public void setWaypoint(String key, String name, int color, ResourceKey<Level> dim, int x, int y, int z) {
        // GTCEu re-sends setWaypoint for the same key on repeat clicks rather than routing
        // through removeWaypoint first — dedupe here so re-clicking a vein moves/renames its
        // waypoint instead of piling up duplicates.
        WaypointManager.remove(key);
        // Silent skip, not a message — this fires from GTCEu's own scanner UI, not a Solaris
        // screen, so there's no good place to surface feedback here.
        if (!WaypointManager.canPlace(dim.location())) return;

        Waypoint waypoint = new Waypoint(name, dim.location(), x, y, z, String.format("%06X", color & 0xFFFFFF));
        waypoint.id = key;
        waypoint.category = "GT Veins";
        WaypointManager.add(waypoint);
    }

    @Override
    public void removeWaypoint(String key) {
        WaypointManager.remove(key);
    }
}
