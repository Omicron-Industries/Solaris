package net.phoenixvine.solaris.network;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.solaris.client.waypoint.Waypoint;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;

import java.util.function.Supplier;

public class S2CReceiveWaypointPacket {

    private final String senderName;
    private final String name;
    private final String dimension;
    private final int x;
    private final int y;
    private final int z;
    private final String color;
    private final String icon;
    private final boolean locked;

    public S2CReceiveWaypointPacket(String senderName, String name, String dimension, int x, int y, int z,
                                    String color, String icon, boolean locked) {
        this.senderName = senderName;
        this.name = name;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.color = color;
        this.icon = icon;
        this.locked = locked;
    }

    public S2CReceiveWaypointPacket(FriendlyByteBuf buf) {
        this.senderName = buf.readUtf(16);
        this.name = buf.readUtf(48);
        this.dimension = buf.readUtf(128);
        this.x = buf.readVarInt();
        this.y = buf.readVarInt();
        this.z = buf.readVarInt();
        this.color = buf.readUtf(8);
        this.icon = buf.readUtf(32);
        this.locked = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(senderName, 16);
        buf.writeUtf(name, 48);
        buf.writeUtf(dimension, 128);
        buf.writeVarInt(x);
        buf.writeVarInt(y);
        buf.writeVarInt(z);
        buf.writeUtf(color, 8);
        buf.writeUtf(icon, 32);
        buf.writeBoolean(locked);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> applyOnClient(this)));
        ctx.get().setPacketHandled(true);
    }

    private static void applyOnClient(S2CReceiveWaypointPacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Waypoint w = new Waypoint(pkt.name, new ResourceLocation(pkt.dimension), pkt.x, pkt.y, pkt.z, pkt.color);
        w.icon = pkt.icon;
        w.locked = pkt.locked;
        WaypointManager.add(w);

        mc.player.sendSystemMessage(Component.literal(
                "Received waypoint '" + pkt.name + "' from " + pkt.senderName + " — added to your list.")
                .withStyle(ChatFormatting.AQUA));
    }
}
