package net.phoenixvine.solaris.server;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.integration.guilds.GuildsIntegration;
import net.phoenixvine.solaris.network.S2CForceUpdateChunkPacket;
import net.phoenixvine.solaris.network.S2CReceiveWaypointPacket;
import net.phoenixvine.solaris.network.S2CRemoveWaypointPacket;
import net.phoenixvine.solaris.network.S2CSyncFeatureStatePacket;
import net.phoenixvine.solaris.network.SolarisNetwork;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative counterpart to {@link net.phoenixvine.solaris.api.SolarisAPI}'s
 * client-side {@code setTier}/{@code setFeatureEnabled} — for exactly the case those two flagged
 * as their own limitation: a value set purely client-side can be spoofed by a modified client,
 * and doesn't survive a relog on its own. This is the "make it real" version other map mods'
 * progression-gating features generally use: a custom item's {@code use()} (or any other
 * server-side trigger — an advancement, a structure visit, a quest completion) calls
 * {@link #setTier}/{@link #setFeatureEnabled} here, which persists it to that player's own save
 * data and immediately pushes it down to their client via {@link S2CSyncFeatureStatePacket} —
 * the calling mod never needs its own networking or persistence for this.
 *
 * Storage: nested under {@code Player.PERSISTED_NBT_TAG} ("PlayerPersisted"), the one player-NBT
 * subtree Forge/vanilla guarantee survive death and respawn, not just an ordinary save/load —
 * losing progression on death would be a much worse bug than losing it on world exit. Reads and
 * writes go straight through {@link ServerPlayer#getPersistentData()}'s live tag graph (each
 * nested {@code getCompound} is re-{@code put} back into its parent immediately, whether it
 * already existed or not, so every mutation after that point is automatically connected all the
 * way up without a separate explicit save step) — the same lightweight, capability-free
 * persistence mechanism this mod family already uses elsewhere for small per-player flags.
 */
public final class SolarisServerAPI {

    private static final String TIERS_KEY = "tiers";
    private static final String FEATURES_KEY = "features";

    private SolarisServerAPI() {}

    /**
     * Sets and persists {@code player}'s progression tier in {@code dimension}, then immediately syncs it to their
     * client.
     */
    public static void setTier(ServerPlayer player, ResourceLocation dimension, int tier) {
        getOrCreateChild(solarisTag(player), TIERS_KEY).putInt(dimension.toString(), tier);
        syncToClient(player);
    }

    /**
     * The persisted tier for {@code player} in {@code dimension} — the server's own source of truth, 0 if never set.
     */
    public static int getTier(ServerPlayer player, ResourceLocation dimension) {
        return getOrCreateChild(solarisTag(player), TIERS_KEY).getInt(dimension.toString());
    }

    /** Sets and persists a named feature flag for {@code player}, then immediately syncs it to their client. */
    public static void setFeatureEnabled(ServerPlayer player, String featureId, boolean enabled) {
        getOrCreateChild(solarisTag(player), FEATURES_KEY).putBoolean(featureId, enabled);
        syncToClient(player);
    }

    /**
     * True unless {@code player} has an explicit persisted {@code false} for {@code featureId} — same "opt-in
     * restriction" default as the client-side gate.
     */
    public static boolean isFeatureEnabled(ServerPlayer player, String featureId) {
        CompoundTag features = getOrCreateChild(solarisTag(player), FEATURES_KEY);
        return !features.contains(featureId) || features.getBoolean(featureId);
    }

    /**
     * Re-sends {@code player}'s full persisted tier/feature snapshot — called automatically by
     * {@link #setTier}/{@link #setFeatureEnabled}/{@link #setFeatureState}, and on login ({@link
     * SolarisServerEvents}), so a fresh session picks up whatever was unlocked before without the
     * calling mod needing to re-trigger anything itself. Safe to call directly too, e.g. to force
     * a resync after externally editing a player's save data.
     *
     * Also includes {@code player}'s resolved team's {@link SolarisFeatureState} snapshot (see
     * {@link #setFeatureState}) — a joining/reconnecting player needs to see their guild's
     * already-configured states immediately, not just their own individual tiers/booleans.
     */
    public static void syncToClient(ServerPlayer player) {
        CompoundTag data = solarisTag(player);
        CompoundTag tiersTag = getOrCreateChild(data, TIERS_KEY);
        CompoundTag featuresTag = getOrCreateChild(data, FEATURES_KEY);

        Map<String, Integer> tiers = new HashMap<>();
        for (String key : tiersTag.getAllKeys()) tiers.put(key, tiersTag.getInt(key));

        Map<String, Boolean> features = new HashMap<>();
        for (String key : featuresTag.getAllKeys()) features.put(key, featuresTag.getBoolean(key));

        UUID token = GuildsIntegration.ownerTokenFor(player);
        Map<String, Map<String, SolarisFeatureState>> states = SolarisFeatureStateData
                .get(player.getServer().overworld()).snapshot(token);

        SolarisNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CSyncFeatureStatePacket(tiers, features, states));
    }

    // ── Team-shared feature states ───────────────────────────────────────────

    /**
     * Sets a {@link SolarisFeatureState} for {@code targetPlayer}'s whole team (their guild if
     * they're in one, otherwise just themselves — see {@link
     * GuildsIntegration#ownerTokenFor}) in {@code dimension}, persists it world-side (survives a
     * restart, not tied to any one player's save data), and immediately syncs every currently
     * -online team member — not just {@code targetPlayer} — so an admin/event granting access to
     * one guild member is visible to the whole online guild at once, with no `/reload` and no
     * relog needed.
     */
    public static void setFeatureState(ServerPlayer targetPlayer, String featureId, ResourceLocation dimension,
                                       SolarisFeatureState state) {
        UUID token = GuildsIntegration.ownerTokenFor(targetPlayer);
        SolarisFeatureStateData.get(targetPlayer.getServer().overworld()).set(token, dimension, featureId, state);

        syncToClient(targetPlayer);
        for (ServerPlayer teammate : GuildsIntegration.getOnlineGuildmates(targetPlayer)) {
            syncToClient(teammate);
        }
    }

    /**
     * The persisted state for {@code player}'s team (or themselves, if solo) — {@link SolarisFeatureState#ENABLED} if
     * never set.
     */
    public static SolarisFeatureState getFeatureState(ServerPlayer player, String featureId,
                                                      ResourceLocation dimension) {
        UUID token = GuildsIntegration.ownerTokenFor(player);
        return SolarisFeatureStateData.get(player.getServer().overworld()).get(token, dimension, featureId);
    }

    // ── Force-update / server-triggered placement ──────────────────────────────

    /**
     * Tells {@code target}'s client to immediately re-sample one chunk on the world map,
     * bypassing their own world-map write gate/range restrictions entirely — that's the point of
     * "force". Skipped server-side (no packet sent) if the chunk isn't generated yet, per the
     * explicit "don't force-generate" requirement; the client independently re-checks the same
     * thing before actually sampling.
     */
    public static void forceUpdateChunk(ServerPlayer target, ResourceLocation dimension, int chunkX, int chunkZ) {
        ServerLevel level = target.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null || !level.getChunkSource().hasChunk(chunkX, chunkZ)) return;

        SolarisNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target),
                new S2CForceUpdateChunkPacket(dimension.toString(), chunkX, chunkZ));
    }

    /**
     * Places a waypoint directly on {@code target}'s client, bypassing their own waypoint-placement
     * gate (an admin/scripted placement is a distinct, intentionally-privileged action, not
     * something the target opted into) — reuses {@code S2CReceiveWaypointPacket}, the same packet
     * guild-sharing already sends, with a {@code "System"} sender name so the existing "Received
     * waypoint..." chat message still reads sensibly for a non-guild-share placement.
     */
    public static void placeWaypoint(ServerPlayer target, String name, ResourceLocation dimension, int x, int y,
                                     int z, String colorHex, boolean locked) {
        SolarisNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target),
                new S2CReceiveWaypointPacket("System", name, dimension.toString(), x, y, z, colorHex,
                        net.phoenixvine.solaris.client.waypoint.WaypointIcon.DOT.name(), locked));
    }

    /** Removes a waypoint from {@code target}'s client by name (see {@link S2CRemoveWaypointPacket}'s own doc). */
    public static void removeWaypoint(ServerPlayer target, String name) {
        SolarisNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), new S2CRemoveWaypointPacket(name));
    }

    private static CompoundTag solarisTag(ServerPlayer player) {
        CompoundTag persisted = getOrCreateChild(player.getPersistentData(), Player.PERSISTED_NBT_TAG);
        return getOrCreateChild(persisted, PhoenixSolaris.MOD_ID);
    }

    /**
     * Fetches {@code key} from {@code parent}, re-linking it in regardless of whether it already existed, so the
     * returned tag is always safe to mutate in place.
     */
    private static CompoundTag getOrCreateChild(CompoundTag parent, String key) {
        CompoundTag child = parent.getCompound(key);
        parent.put(key, child);
        return child;
    }
}
