package net.phoenixvine.solaris.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Pushes a player's server-persisted progression tiers, feature flags, and (team-shared, see
 * {@code SolarisServerAPI#setFeatureState}) {@link SolarisFeatureState}s down to their own
 * client, applying them onto the exact same client-side state {@link SolarisAPI#setTier}/{@link
 * SolarisAPI#setFeatureEnabled}/{@link SolarisAPI#setFeatureState} already maintain — a mod
 * calling the server-side API doesn't need to know or care that this packet exists at all, it's
 * purely the plumbing that makes a server-authoritative call show up on the right client. Sent
 * once on login (a full snapshot) and again on every subsequent server-side change — including to
 * every other online teammate when a {@link SolarisFeatureState} changes, not just the player who
 * triggered it.
 */
public class S2CSyncFeatureStatePacket {

    private final Map<String, Integer> tiers;
    private final Map<String, Boolean> features;
    /** dimension (as string) -> featureId -> state. */
    private final Map<String, Map<String, SolarisFeatureState>> states;

    public S2CSyncFeatureStatePacket(Map<String, Integer> tiers, Map<String, Boolean> features,
                                     Map<String, Map<String, SolarisFeatureState>> states) {
        this.tiers = tiers;
        this.features = features;
        this.states = states;
    }

    public S2CSyncFeatureStatePacket(FriendlyByteBuf buf) {
        int tierCount = buf.readVarInt();
        tiers = new HashMap<>();
        for (int i = 0; i < tierCount; i++) {
            tiers.put(buf.readUtf(128), buf.readVarInt());
        }

        int featureCount = buf.readVarInt();
        features = new HashMap<>();
        for (int i = 0; i < featureCount; i++) {
            features.put(buf.readUtf(64), buf.readBoolean());
        }

        int dimensionCount = buf.readVarInt();
        states = new HashMap<>();
        for (int i = 0; i < dimensionCount; i++) {
            String dimension = buf.readUtf(128);
            int perDimensionCount = buf.readVarInt();
            Map<String, SolarisFeatureState> perDimension = new HashMap<>();
            for (int j = 0; j < perDimensionCount; j++) {
                String featureId = buf.readUtf(64);
                perDimension.put(featureId, buf.readEnum(SolarisFeatureState.class));
            }
            states.put(dimension, perDimension);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(tiers.size());
        for (Map.Entry<String, Integer> entry : tiers.entrySet()) {
            buf.writeUtf(entry.getKey(), 128);
            buf.writeVarInt(entry.getValue());
        }

        buf.writeVarInt(features.size());
        for (Map.Entry<String, Boolean> entry : features.entrySet()) {
            buf.writeUtf(entry.getKey(), 64);
            buf.writeBoolean(entry.getValue());
        }

        buf.writeVarInt(states.size());
        for (Map.Entry<String, Map<String, SolarisFeatureState>> dimensionEntry : states.entrySet()) {
            buf.writeUtf(dimensionEntry.getKey(), 128);
            buf.writeVarInt(dimensionEntry.getValue().size());
            for (Map.Entry<String, SolarisFeatureState> featureEntry : dimensionEntry.getValue().entrySet()) {
                buf.writeUtf(featureEntry.getKey(), 64);
                buf.writeEnum(featureEntry.getValue());
            }
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> applyOnClient(this)));
        ctx.get().setPacketHandled(true);
    }

    private static void applyOnClient(S2CSyncFeatureStatePacket pkt) {
        if (Minecraft.getInstance().player == null) return;

        for (Map.Entry<String, Integer> entry : pkt.tiers.entrySet()) {
            SolarisAPI.setTier(new ResourceLocation(entry.getKey()), entry.getValue());
        }
        for (Map.Entry<String, Boolean> entry : pkt.features.entrySet()) {
            SolarisAPI.setFeatureEnabled(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Map<String, SolarisFeatureState>> dimensionEntry : pkt.states.entrySet()) {
            ResourceLocation dimension = new ResourceLocation(dimensionEntry.getKey());
            for (Map.Entry<String, SolarisFeatureState> featureEntry : dimensionEntry.getValue().entrySet()) {
                SolarisAPI.setFeatureState(featureEntry.getKey(), dimension, featureEntry.getValue());
            }
        }
    }
}
