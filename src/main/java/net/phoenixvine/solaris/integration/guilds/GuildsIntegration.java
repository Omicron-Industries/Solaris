package net.phoenixvine.solaris.integration.guilds;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.phoenixvine.guilds.GuildAPI;
import net.phoenixvine.solaris.PhoenixSolaris;

import java.util.List;
import java.util.UUID;

public final class GuildsIntegration {

    public static final String GUILDS_MOD_ID = "phoenix_guilds";

    private GuildsIntegration() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded(GUILDS_MOD_ID);
    }

    public static List<ServerPlayer> getOnlineGuildmates(ServerPlayer player) {
        if (!isAvailable()) return List.of();
        try {
            return GuildAPI.getOnlineGuildMembers(player.getUUID()).stream()
                    .filter(mate -> !mate.getUUID().equals(player.getUUID()))
                    .toList();
        } catch (Exception e) {

            PhoenixSolaris.LOGGER.warn("Guilds integration failed", e);
            return List.of();
        }
    }

    public static UUID ownerTokenFor(ServerPlayer player) {
        if (!isAvailable()) return player.getUUID();
        try {
            return GuildAPI.getGuildIdOrPlayerFallback(player.getUUID());
        } catch (Exception e) {
            PhoenixSolaris.LOGGER.warn("Guilds integration failed", e);
            return player.getUUID();
        }
    }
}
