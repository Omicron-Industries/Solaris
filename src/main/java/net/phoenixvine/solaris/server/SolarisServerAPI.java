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

public final class SolarisServerAPI {

    private static final String TIERS_KEY = "tiers";
    private static final String FEATURES_KEY = "features";

    private SolarisServerAPI() {}

    public static void setTier(ServerPlayer player, ResourceLocation dimension, int tier) {
        getOrCreateChild(solarisTag(player), TIERS_KEY).putInt(dimension.toString(), tier);
        syncToClient(player);
    }

    public static int getTier(ServerPlayer player, ResourceLocation dimension) {
        return getOrCreateChild(solarisTag(player), TIERS_KEY).getInt(dimension.toString());
    }

    public static void setFeatureEnabled(ServerPlayer player, String featureId, boolean enabled) {
        getOrCreateChild(solarisTag(player), FEATURES_KEY).putBoolean(featureId, enabled);
        syncToClient(player);
    }

    public static boolean isFeatureEnabled(ServerPlayer player, String featureId) {
        CompoundTag features = getOrCreateChild(solarisTag(player), FEATURES_KEY);
        return !features.contains(featureId) || features.getBoolean(featureId);
    }

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

    public static void setFeatureState(ServerPlayer targetPlayer, String featureId, ResourceLocation dimension,
                                       SolarisFeatureState state) {
        UUID token = GuildsIntegration.ownerTokenFor(targetPlayer);
        SolarisFeatureStateData.get(targetPlayer.getServer().overworld()).set(token, dimension, featureId, state);

        syncToClient(targetPlayer);
        for (ServerPlayer teammate : GuildsIntegration.getOnlineGuildmates(targetPlayer)) {
            syncToClient(teammate);
        }
    }

    public static SolarisFeatureState getFeatureState(ServerPlayer player, String featureId,
                                                      ResourceLocation dimension) {
        UUID token = GuildsIntegration.ownerTokenFor(player);
        return SolarisFeatureStateData.get(player.getServer().overworld()).get(token, dimension, featureId);
    }

    public static void forceUpdateChunk(ServerPlayer target, ResourceLocation dimension, int chunkX, int chunkZ) {
        ServerLevel level = target.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null || !level.getChunkSource().hasChunk(chunkX, chunkZ)) return;

        SolarisNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target),
                new S2CForceUpdateChunkPacket(dimension.toString(), chunkX, chunkZ));
    }

    public static void placeWaypoint(ServerPlayer target, String name, ResourceLocation dimension, int x, int y,
                                     int z, String colorHex, boolean locked) {
        SolarisNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target),
                new S2CReceiveWaypointPacket("System", name, dimension.toString(), x, y, z, colorHex,
                        net.phoenixvine.solaris.client.waypoint.WaypointIcon.DOT.name(), locked));
    }

    public static void removeWaypoint(ServerPlayer target, String name) {
        SolarisNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), new S2CRemoveWaypointPacket(name));
    }

    private static CompoundTag solarisTag(ServerPlayer player) {
        CompoundTag persisted = getOrCreateChild(player.getPersistentData(), Player.PERSISTED_NBT_TAG);
        return getOrCreateChild(persisted, PhoenixSolaris.MOD_ID);
    }

    private static CompoundTag getOrCreateChild(CompoundTag parent, String key) {
        CompoundTag child = parent.getCompound(key);
        parent.put(key, child);
        return child;
    }
}
