package net.phoenixvine.solaris.api;

import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.client.overlay.SolarisOverlay;
import net.phoenixvine.solaris.client.overlay.SolarisOverlayRegistry;
import net.phoenixvine.solaris.client.render.SolarisTexture;
import net.phoenixvine.solaris.client.waypoint.Waypoint;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Stable public API for Solaris. Mirrors the {@code GuildAPI}/{@code QuestAPI}/
 * {@code DomainAPI} house style: a final class, private constructor, static methods.
 *
 * Client-side only, except {@link #isFeatureEnabled} (see its own doc) — everything else here
 * is safe to call from any client-side code; a no-op guard isn't needed here the way it is for
 * the server-side APIs, since the overlay registry and texture cache are themselves plain
 * client-side statics.
 *
 * <h2>Feature gates and progression tiers</h2>
 * Two composable layers, both landing on the same {@link #isFeatureEnabled} check:
 * <ul>
 * <li>{@link #registerFeatureGate} — a raw, arbitrary {@code BooleanSupplier}. The maximally
 * flexible primitive: read whatever state you want (a permission, a claim, a supporter flag),
 * return true/false.</li>
 * <li>{@link #setTier}/{@link #requireTier} — a numeric per-dimension progression tier on top of
 * that, for the far more common "unlock feature X once the player reaches tier N" shape, without
 * hand-rolling a supplier for every feature and manually recomputing it on every advancement.
 * </li>
 * </ul>
 * Example — an endgame item ("Omega Beacon") that should unlock precise coordinate display once
 * a player has both visited the Overworld's origin column and reached a tech tier, with a
 * separate (higher) requirement in the Nether:
 *
 * <pre>{@code
 * // once, at setup — declare what each dimension's tier gates
 * SolarisAPI.requireTier(SolarisAPI.FEATURE_SHOW_COORDINATES, overworld, 2);
 * SolarisAPI.requireTier(SolarisAPI.FEATURE_SHOW_COORDINATES, nether, 5);
 *
 * // whenever the player's own progression actually advances
 * SolarisAPI.setTier(overworld, hasVisitedOrigin && hasReachedLv ? 2 : 0);
 * }</pre>
 *
 * Solaris itself never defines what a "tier" means beyond a plain {@code int} — visiting a
 * coordinate, reaching a tech tier, or anything else is entirely up to whatever external system
 * calls {@link #setTier}.
 *
 * <h2>Client-only vs. server-authoritative</h2>
 * {@link #setTier}/{@link #setFeatureEnabled} here are pure client-side state: nothing stops a
 * modified client from calling them directly to unlock everything, and nothing here survives a
 * relog on its own. That's fine for a purely cosmetic, single-client toggle — but the item-use
 * case above almost always actually wants {@code net.phoenixvine.solaris.server.SolarisServerAPI}
 * instead: the same {@code setTier}/{@code setFeatureEnabled} shape, called from the item's own
 * server-side {@code use()}, which persists to that player's save data (survives death and
 * relog) and pushes the result down to their client automatically — the calling mod needs no
 * networking or persistence of its own for this. This class's own {@code setTier}/{@code
 * setFeatureEnabled} remain exactly as they were for local-only/testing use; {@code
 * SolarisServerAPI} is additive, not a replacement.
 *
 * <h2>Three-way feature states</h2>
 * {@link #setFeatureState}/{@link #getFeatureState} are a third layer for features with a real
 * middle ground between off and fully on — see {@link SolarisFeatureState}'s own doc. Same
 * client-only-by-default/server-authoritative-for-real split as above: {@code
 * SolarisServerAPI#setFeatureState} is additionally **team-shared** (a guild's worth of players,
 * not just one) rather than per-player, since these specific features (waypoints, the minimap,
 * the world map, underground rendering) were asked for as something a whole team unlocks
 * together, not an individual progression flag.
 */
public final class SolarisAPI {

    private SolarisAPI() {}

    // ── Feature gates ────────────────────────────────────────────────────────

    /** {@link #registerFeatureGate}'s standard well-known feature ids — see {@link #isFeatureEnabled}'s doc. */
    public static final String FEATURE_GLOBE_VIEW = "globe_view";
    public static final String FEATURE_SHAPE_PLANNER = "shape_planner";
    public static final String FEATURE_PNG_EXPORT = "png_export";
    public static final String FEATURE_GUILD_SHARE = "guild_share";
    public static final String FEATURE_FULLSCREEN_MAP = "fullscreen_map";
    /**
     * Precise X/Y/Z display — the fullscreen map's hovered-block tooltip, currently the one wired enforcement point.
     */
    public static final String FEATURE_SHOW_COORDINATES = "show_coordinates";

    /** {@link SolarisFeatureState}-based feature ids — see {@link #setFeatureState}. */
    public static final String FEATURE_WAYPOINTS = "waypoints";
    public static final String FEATURE_MINIMAP = "minimap";
    public static final String FEATURE_WORLD_MAP = "world_map";
    public static final String FEATURE_UNDERGROUND_MAP = "underground_map";

    private static final Map<String, BooleanSupplier> FEATURE_GATES = new ConcurrentHashMap<>();

    /**
     * Registers (or replaces) a live check for whether {@code featureId} is currently allowed —
     * an open-ended extension point for any mod/packdev to restrict or unlock specific Solaris
     * features, e.g. a donor-perk system unlocking the globe view for supporters, or a claims mod
     * restricting the shape planner outside a player's own territory. Not tied to any one system
     * (Guilds rank, Domains claims, a supporter tier, a datapack flag) on purpose — {@code check}
     * can read whatever state the caller actually cares about. Re-evaluated on every {@link
     * #isFeatureEnabled} call (not cached), so it can reflect state that changes without Solaris
     * needing to know when.
     *
     * For the common "unlocked once the player reaches tier N" shape specifically, {@link
     * #requireTier} is usually a better fit than hand-writing a supplier here — see the class doc.
     *
     * {@code featureId} is a plain string, not an enum — {@link #FEATURE_GLOBE_VIEW} and friends
     * are the ids Solaris itself checks at its own gateable entry points (see each field's
     * reference for exactly where), but nothing stops a caller registering/checking its own
     * separate ids as a general-purpose per-player flag store instead.
     *
     * This is a client-side-only, cosmetic gate — it hides/blocks the corresponding UI action
     * locally, the same way any other client-rendered menu could be. It is not a security
     * boundary: nothing stops a modified client from ignoring it, the way nothing stops one from
     * ignoring any other purely client-side restriction. Use a server-authoritative check instead
     * for anything that actually needs to be enforced against a hostile client.
     */
    public static void registerFeatureGate(String featureId, BooleanSupplier check) {
        FEATURE_GATES.put(featureId, check);
    }

    /** Convenience over {@link #registerFeatureGate} for a plain static on/off flag rather than a dynamic check. */
    public static void setFeatureEnabled(String featureId, boolean enabled) {
        registerFeatureGate(featureId, () -> enabled);
    }

    /** Removes any gate registered for {@code featureId} — it goes back to always-enabled. */
    public static void clearFeatureGate(String featureId) {
        FEATURE_GATES.remove(featureId);
    }

    // ── Progression tiers ────────────────────────────────────────────────────

    private static final Map<ResourceLocation, Integer> DIMENSION_TIERS = new ConcurrentHashMap<>();
    private static final Map<String, Map<ResourceLocation, Integer>> TIER_REQUIREMENTS = new ConcurrentHashMap<>();

    /**
     * Sets the player's current progression tier in {@code dimension} — a plain, caller-defined
     * integer (0 means "nothing beyond baseline"), checked against whatever {@link #requireTier}
     * thresholds are registered against it. Deliberately per-dimension, not global — the same
     * player can be well past unlocking everything in the Overworld while starting from zero in
     * the Nether, and a single global number can't represent that. Forces open maps to redraw
     * (same as {@link #requestRefresh}), so a feature that just unlocked shows up immediately
     * instead of waiting for the next chunk-boundary rebuild.
     */
    public static void setTier(ResourceLocation dimension, int tier) {
        DIMENSION_TIERS.put(dimension, tier);
        requestRefresh();
    }

    /** The tier last set via {@link #setTier} for {@code dimension}, or 0 if never set. */
    public static int getTier(ResourceLocation dimension) {
        return DIMENSION_TIERS.getOrDefault(dimension, 0);
    }

    /**
     * Declares that {@code featureId} needs at least {@code requiredTier} in {@code dimension}
     * (per {@link #getTier}) to count as enabled there — checked automatically by the
     * dimension-aware {@link #isFeatureEnabled(String, ResourceLocation)} overload, alongside any
     * {@link #registerFeatureGate} check for the same id (both must pass). No requirement
     * registered for a given {@code (featureId, dimension)} pair means no tier restriction
     * applies in that dimension specifically — set requirements per dimension independently, they
     * don't inherit or fall back to each other.
     */
    public static void requireTier(String featureId, ResourceLocation dimension, int requiredTier) {
        TIER_REQUIREMENTS.computeIfAbsent(featureId, id -> new ConcurrentHashMap<>()).put(dimension, requiredTier);
    }

    /** Removes the tier requirement (if any) for {@code featureId} in {@code dimension} specifically. */
    public static void clearTierRequirement(String featureId, ResourceLocation dimension) {
        Map<ResourceLocation, Integer> perDimension = TIER_REQUIREMENTS.get(featureId);
        if (perDimension != null) perDimension.remove(dimension);
    }

    /**
     * True (feature allowed) if no gate is registered for {@code featureId} at all — restriction
     * is opt-in per feature, not access. A registered gate that itself throws is treated as
     * "allowed" too (logged, not propagated), so a broken external check can't lock a feature out
     * entirely; the caller should fix their check, but Solaris's own map shouldn't break over it.
     *
     * Ignores {@link #requireTier} entirely — there's no dimension to check tiers against here.
     * Prefer {@link #isFeatureEnabled(String, ResourceLocation)} wherever a dimension is actually
     * known (every current Solaris UI call site is); this overload exists for callers that
     * genuinely only care about the raw gate.
     */
    public static boolean isFeatureEnabled(String featureId) {
        return checkGate(featureId);
    }

    /**
     * Dimension-aware form of {@link #isFeatureEnabled(String)} — also requires any {@link
     * #requireTier} threshold registered for {@code (featureId, dimension)} to be met by {@link
     * #getTier(ResourceLocation)}. Both this and the raw gate must pass; either one alone can
     * still restrict the feature.
     */
    public static boolean isFeatureEnabled(String featureId, ResourceLocation dimension) {
        if (!checkGate(featureId)) return false;

        Map<ResourceLocation, Integer> perDimension = TIER_REQUIREMENTS.get(featureId);
        if (perDimension == null) return true;
        Integer required = perDimension.get(dimension);
        if (required == null) return true;
        return getTier(dimension) >= required;
    }

    private static boolean checkGate(String featureId) {
        BooleanSupplier check = FEATURE_GATES.get(featureId);
        if (check == null) return true;
        try {
            return check.getAsBoolean();
        } catch (Exception e) {
            PhoenixSolaris.LOGGER.warn("Feature gate for '{}' threw, defaulting to enabled", featureId, e);
            return true;
        }
    }

    // ── Feature states (three-way: disabled / visible / enabled) ──────────────

    private static final Map<String, Map<ResourceLocation, SolarisFeatureState>> FEATURE_STATES = new ConcurrentHashMap<>();

    /**
     * Local (client-side) mirror of {@link SolarisFeatureState} for {@code featureId} in {@code
     * dimension} — set directly for local-only/testing use, or (far more commonly for anything
     * team-shared/persisted) overwritten automatically whenever {@code
     * net.phoenixvine.solaris.server.SolarisServerAPI#setFeatureState} pushes a sync packet down.
     * Calling this directly does not persist or sync anywhere on its own — see that class for the
     * server-authoritative, team-shared version. {@link SolarisFeatureState#ENABLED} (fully open)
     * is the default for any {@code (featureId, dimension)} pair that's never been set, matching
     * this whole API's "restriction is opt-in" philosophy.
     */
    public static void setFeatureState(String featureId, ResourceLocation dimension, SolarisFeatureState state) {
        FEATURE_STATES.computeIfAbsent(featureId, id -> new ConcurrentHashMap<>()).put(dimension, state);
        requestRefresh();
    }

    public static SolarisFeatureState getFeatureState(String featureId, ResourceLocation dimension) {
        Map<ResourceLocation, SolarisFeatureState> perDimension = FEATURE_STATES.get(featureId);
        if (perDimension == null) return SolarisFeatureState.ENABLED;
        return perDimension.getOrDefault(dimension, SolarisFeatureState.ENABLED);
    }

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

    /**
     * Adds a waypoint (persisted per-world) and returns it, so the caller can keep a reference —
     * {@code null} if {@link #FEATURE_WAYPOINTS} isn't at least {@code ENABLED} in {@code
     * dimension} (see {@link net.phoenixvine.solaris.client.waypoint.WaypointManager#canPlace}),
     * since this goes through the same regular placement path any other in-game action does. A
     * server-side admin/scripted override that bypasses this exists as {@code
     * SolarisServerAPI#placeWaypoint} instead — deliberately not this method, since "force place
     * regardless of the toggle" should be an explicit, distinct call, not a hidden side effect of
     * the normal one.
     */
    public static Waypoint addWaypoint(String name, ResourceLocation dimension, int x, int y, int z, String colorHex) {
        if (!WaypointManager.canPlace(dimension)) return null;
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
