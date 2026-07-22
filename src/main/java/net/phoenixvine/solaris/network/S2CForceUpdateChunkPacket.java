package net.phoenixvine.solaris.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.solaris.client.color.ChunkColorEvents;
import net.phoenixvine.solaris.client.color.ChunkKey;
import net.phoenixvine.solaris.client.render.SolarisTexture;

import java.util.function.Supplier;

public class S2CForceUpdateChunkPacket {

    private final String dimension;
    private final int chunkX;
    private final int chunkZ;

    public S2CForceUpdateChunkPacket(String dimension, int chunkX, int chunkZ) {
        this.dimension = dimension;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public S2CForceUpdateChunkPacket(FriendlyByteBuf buf) {
        this.dimension = buf.readUtf(128);
        this.chunkX = buf.readVarInt();
        this.chunkZ = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(dimension, 128);
        buf.writeVarInt(chunkX);
        buf.writeVarInt(chunkZ);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> applyOnClient(this)));
        ctx.get().setPacketHandled(true);
    }

    private static void applyOnClient(S2CForceUpdateChunkPacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || !level.dimension().location().toString().equals(pkt.dimension)) return;

        if (!level.hasChunk(pkt.chunkX, pkt.chunkZ)) return;

        ChunkPos pos = new ChunkPos(pkt.chunkX, pkt.chunkZ);
        ChunkKey key = ChunkKey.of(level, pos);
        ChunkColorEvents.resample(level, key, pos);
        SolarisTexture.invalidateAll();
    }
}
