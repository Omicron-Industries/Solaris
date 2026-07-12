package net.phoenixvine.solaris.integration.guilds;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.phoenixvine.guilds.GuildAPI;
import net.phoenixvine.solaris.PhoenixSolaris;

import java.util.List;
import java.util.UUID;

/**
 * Optional integration with Phoenix Guilds: routes a shared waypoint to a player's online
 * guildmates instead of broadcasting it to the whole server. {@code phoenix_guilds} is declared
 * as a non-mandatory {@code mods.toml} dependency, so nothing in this class may be touched unless
 * {@link #isAvailable()} is true first — mirrors {@code integration.gtceu.GtceuIntegration}
 * exactly, just with a locally-built sibling-repo jar (see Solaris's {@code build.gradle}'s
 * {@code flatDir}/{@code local:phoenix_guilds} dependency, populated by Guilds' own {@code
 * publishToSolaris} task) instead of a published Maven artifact.
 *
 * {@link GuildAPI} is itself documented as server-side only — every method here must only ever be
 * called from packet-handling code running on the server (a {@code C2S} packet's {@code handle}),
 * never from client-side code.
 */
public final class GuildsIntegration {

    public static final String GUILDS_MOD_ID = "phoenix_guilds";

    private GuildsIntegration() {}

    /** Safe to call from either side — a plain {@code ModList} presence check, not a Guilds API call. */
    public static boolean isAvailable() {
        return ModList.get().isLoaded(GUILDS_MOD_ID);
    }

    /**
     * Every other online member of {@code player}'s guild (never includes {@code player}
     * themselves), or an empty list if Guilds isn't installed or the player isn't in a guild.
     * Server-side only — see the class doc.
     */
    public static List<ServerPlayer> getOnlineGuildmates(ServerPlayer player) {
        if (!isAvailable()) return List.of();
        try {
            return GuildAPI.getOnlineGuildMembers(player.getUUID()).stream()
                    .filter(mate -> !mate.getUUID().equals(player.getUUID()))
                    .toList();
        } catch (Exception e) {
            // Same defensive latch GtceuIntegration uses — an integration touching another
            // mod's internals shouldn't be able to break Solaris's own core map function if
            // that mod's API ever shifts under it.
            PhoenixSolaris.LOGGER.warn("Guilds integration failed", e);
            return List.of();
        }
    }

    /**
     * The player's guild UUID if they're in one, otherwise their own UUID — the "team-or-solo"
     * ownership token used for anything that should be shared across a whole guild but still work
     * sensibly for an unguilded player. Mirrors {@code GuildAPI.getGuildIdOrPlayerFallback}
     * exactly (itself documented there as mirroring Phoenix Domains' own {@code
     * TeamUtils.getTeamIdOrPlayerFallback} for claim ownership) — reusing the same convention
     * rather than inventing a new one for Solaris's own team-shared feature-state storage.
     */
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
