package net.phoenixvine.solaris.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.phoenixvine.solaris.PhoenixSolaris;

import java.util.Optional;

public final class SolarisNetwork {

    private static final String PROTOCOL = "1";

    public static SimpleChannel CHANNEL;

    private static int id = 0;

    private SolarisNetwork() {}

    public static void init() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(PhoenixSolaris.MOD_ID, "main"),
                () -> PROTOCOL,
                PROTOCOL::equals,
                PROTOCOL::equals);

        CHANNEL.registerMessage(id++,
                C2SShareWaypointPacket.class,
                C2SShareWaypointPacket::encode,
                C2SShareWaypointPacket::new,
                C2SShareWaypointPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++,
                S2CReceiveWaypointPacket.class,
                S2CReceiveWaypointPacket::encode,
                S2CReceiveWaypointPacket::new,
                S2CReceiveWaypointPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                S2CSyncFeatureStatePacket.class,
                S2CSyncFeatureStatePacket::encode,
                S2CSyncFeatureStatePacket::new,
                S2CSyncFeatureStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                S2CForceUpdateChunkPacket.class,
                S2CForceUpdateChunkPacket::encode,
                S2CForceUpdateChunkPacket::new,
                S2CForceUpdateChunkPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                S2CRemoveWaypointPacket.class,
                S2CRemoveWaypointPacket::encode,
                S2CRemoveWaypointPacket::new,
                S2CRemoveWaypointPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
