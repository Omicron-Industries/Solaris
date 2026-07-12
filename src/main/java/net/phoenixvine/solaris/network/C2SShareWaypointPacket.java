package net.phoenixvine.solaris.network;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.phoenixvine.solaris.integration.guilds.GuildsIntegration;

import java.util.List;
import java.util.function.Supplier;

/**
 * Client asks the server to route one of its waypoints to the sender's online guildmates.
 * Deliberately doesn't trust the client's own idea of "am I in a guild" for anything beyond
 * showing/hiding the "Share to Guild" button — {@link GuildsIntegration#getOnlineGuildmates} runs
 * server-side, where guild membership actually lives, so a modified/stale client can't spoof
 * sharing to players it isn't really guilded with.
 */
public class C2SShareWaypointPacket {

    private final String name;
    private final String dimension;
    private final int x;
    private final int y;
    private final int z;
    private final String color;
    private final String icon;

    public C2SShareWaypointPacket(String name, String dimension, int x, int y, int z, String color, String icon) {
        this.name = name;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.color = color;
        this.icon = icon;
    }

    public C2SShareWaypointPacket(FriendlyByteBuf buf) {
        this.name = buf.readUtf(48);
        this.dimension = buf.readUtf(128);
        this.x = buf.readVarInt();
        this.y = buf.readVarInt();
        this.z = buf.readVarInt();
        this.color = buf.readUtf(8);
        this.icon = buf.readUtf(32);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(name, 48);
        buf.writeUtf(dimension, 128);
        buf.writeVarInt(x);
        buf.writeVarInt(y);
        buf.writeVarInt(z);
        buf.writeUtf(color, 8);
        buf.writeUtf(icon, 32);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            if (!GuildsIntegration.isAvailable()) {
                sender.sendSystemMessage(
                        Component.literal("Guilds isn't installed on this server.").withStyle(ChatFormatting.RED));
                return;
            }

            List<ServerPlayer> guildmates = GuildsIntegration.getOnlineGuildmates(sender);
            if (guildmates.isEmpty()) {
                sender.sendSystemMessage(Component.literal("No online guildmates to share with.")
                        .withStyle(ChatFormatting.YELLOW));
                return;
            }

            S2CReceiveWaypointPacket outgoing = new S2CReceiveWaypointPacket(sender.getGameProfile().getName(),
                    name, dimension, x, y, z, color, icon, false);
            for (ServerPlayer mate : guildmates) {
                SolarisNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> mate), outgoing);
            }

            sender.sendSystemMessage(Component.literal("Shared waypoint '" + name + "' with " + guildmates.size() +
                    " guildmate(s).").withStyle(ChatFormatting.GREEN));
        });
        ctx.get().setPacketHandled(true);
    }
}
