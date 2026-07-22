package net.phoenixvine.solaris.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;

import java.util.function.Supplier;

public class S2CRemoveWaypointPacket {

    private final String name;

    public S2CRemoveWaypointPacket(String name) {
        this.name = name;
    }

    public S2CRemoveWaypointPacket(FriendlyByteBuf buf) {
        this.name = buf.readUtf(48);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(name, 48);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> {
                    if (Minecraft.getInstance().player != null) WaypointManager.removeByName(name);
                }));
        ctx.get().setPacketHandled(true);
    }
}
