package net.phoenixvine.solaris.server.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.solaris.PhoenixSolaris;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.server.SolarisServerAPI;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

/**
 * Thin command wrappers over {@link SolarisServerAPI} — every handler is a one-line call into an
 * already-public static method, the same shape a KubeJS/QuestBuilder script would call directly
 * via plain Java interop. Mirrors {@code Phoenix-Domains}' own {@code DomainCommands} shape:
 * a single {@code RegisterCommandsEvent} subscriber, {@code Commands.literal(...).then(...)}
 * nesting, {@code hasPermission(2)} admin-gated throughout (every branch here is an
 * admin/scripting surface, not a player-facing convenience command).
 */
@Mod.EventBusSubscriber(modid = PhoenixSolaris.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SolarisCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var d = event.getDispatcher();

        var featureSet = Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .then(Commands.argument("feature", StringArgumentType.word())
                                .then(Commands.argument("state", StringArgumentType.word())
                                        .executes(ctx -> featureSet(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                DimensionArgument.getDimension(ctx, "dimension"),
                                                StringArgumentType.getString(ctx, "feature"),
                                                StringArgumentType.getString(ctx, "state"))))));

        var featureGet = Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .then(Commands.argument("feature", StringArgumentType.word())
                                .executes(ctx -> featureGet(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"),
                                        DimensionArgument.getDimension(ctx, "dimension"),
                                        StringArgumentType.getString(ctx, "feature")))));

        var waypointPlaceLocked = Commands.argument("locked", BoolArgumentType.bool())
                .executes(ctx -> waypointPlace(ctx.getSource(),
                        EntityArgument.getPlayer(ctx, "player"),
                        StringArgumentType.getString(ctx, "name"),
                        DimensionArgument.getDimension(ctx, "dimension"),
                        IntegerArgumentType.getInteger(ctx, "x"),
                        IntegerArgumentType.getInteger(ctx, "y"),
                        IntegerArgumentType.getInteger(ctx, "z"),
                        StringArgumentType.getString(ctx, "color"),
                        BoolArgumentType.getBool(ctx, "locked")));

        var waypointPlaceColor = Commands.argument("color", StringArgumentType.word())
                .executes(ctx -> waypointPlace(ctx.getSource(),
                        EntityArgument.getPlayer(ctx, "player"),
                        StringArgumentType.getString(ctx, "name"),
                        DimensionArgument.getDimension(ctx, "dimension"),
                        IntegerArgumentType.getInteger(ctx, "x"),
                        IntegerArgumentType.getInteger(ctx, "y"),
                        IntegerArgumentType.getInteger(ctx, "z"),
                        StringArgumentType.getString(ctx, "color"),
                        false))
                .then(waypointPlaceLocked);

        var waypointPlaceXyz = Commands.argument("x", IntegerArgumentType.integer())
                .then(Commands.argument("y", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                .then(waypointPlaceColor)));

        var waypointPlace = Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .then(waypointPlaceXyz)));

        var waypointRemove = Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> waypointRemove(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                StringArgumentType.getString(ctx, "name"))));

        var chunkForceUpdate = Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(ctx -> chunkForceUpdate(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                DimensionArgument.getDimension(ctx, "dimension"),
                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                IntegerArgumentType.getInteger(ctx, "z"))))));

        d.register(Commands.literal("solaris")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("feature")
                        .then(Commands.literal("set").then(featureSet))
                        .then(Commands.literal("get").then(featureGet)))
                .then(Commands.literal("waypoint")
                        .then(Commands.literal("place").then(waypointPlace))
                        .then(Commands.literal("remove").then(waypointRemove)))
                .then(Commands.literal("chunk")
                        .then(Commands.literal("forceupdate").then(chunkForceUpdate))));
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private static int featureSet(CommandSourceStack source, ServerPlayer target, ServerLevel dimensionLevel,
                                  String featureName, String stateName) {
        String featureId = featureId(featureName);
        if (featureId == null) {
            source.sendFailure(Component.literal("Unknown feature '" + featureName + "'."));
            return 0;
        }
        SolarisFeatureState state;
        try {
            state = SolarisFeatureState.valueOf(stateName.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Unknown state '" + stateName +
                    "' — expected disabled, visible, or enabled."));
            return 0;
        }

        ResourceLocation dimension = dimensionLevel.dimension().location();
        SolarisServerAPI.setFeatureState(target, featureId, dimension, state);
        source.sendSuccess(() -> Component.literal(
                featureName + " for " + target.getGameProfile().getName() + " in " + dimension + " set to " +
                        state.name().toLowerCase(java.util.Locale.ROOT) + "."),
                true);
        return 1;
    }

    private static int featureGet(CommandSourceStack source, ServerPlayer target, ServerLevel dimensionLevel,
                                  String featureName) {
        String featureId = featureId(featureName);
        if (featureId == null) {
            source.sendFailure(Component.literal("Unknown feature '" + featureName + "'."));
            return 0;
        }

        ResourceLocation dimension = dimensionLevel.dimension().location();
        SolarisFeatureState state = SolarisServerAPI.getFeatureState(target, featureId, dimension);
        source.sendSuccess(() -> Component.literal(
                featureName + " for " + target.getGameProfile().getName() + " in " + dimension + " is " +
                        state.name().toLowerCase(java.util.Locale.ROOT) + "."),
                false);
        return 1;
    }

    private static int waypointPlace(CommandSourceStack source, ServerPlayer target, String name,
                                     ServerLevel dimensionLevel, int x, int y, int z, String color,
                                     boolean locked) {
        ResourceLocation dimension = dimensionLevel.dimension().location();
        SolarisServerAPI.placeWaypoint(target, name, dimension, x, y, z, color, locked);
        source.sendSuccess(() -> Component.literal(
                "Placed waypoint '" + name + "' for " + target.getGameProfile().getName() + "."), true);
        return 1;
    }

    private static int waypointRemove(CommandSourceStack source, ServerPlayer target, String name) {
        SolarisServerAPI.removeWaypoint(target, name);
        source.sendSuccess(() -> Component.literal(
                "Removed waypoint '" + name + "' from " + target.getGameProfile().getName() + "."), true);
        return 1;
    }

    private static int chunkForceUpdate(CommandSourceStack source, ServerPlayer target, ServerLevel dimensionLevel,
                                        int chunkX, int chunkZ) {
        ResourceLocation dimension = dimensionLevel.dimension().location();
        SolarisServerAPI.forceUpdateChunk(target, dimension, chunkX, chunkZ);
        source.sendSuccess(() -> Component.literal(
                "Requested force-update of chunk (" + chunkX + ", " + chunkZ + ") for " +
                        target.getGameProfile().getName() + "."),
                true);
        return 1;
    }

    private static String featureId(String featureName) {
        return switch (featureName.toLowerCase(java.util.Locale.ROOT)) {
            case "waypoints" -> SolarisAPI.FEATURE_WAYPOINTS;
            case "minimap" -> SolarisAPI.FEATURE_MINIMAP;
            case "worldmap" -> SolarisAPI.FEATURE_WORLD_MAP;
            case "undergroundmap" -> SolarisAPI.FEATURE_UNDERGROUND_MAP;
            default -> null;
        };
    }
}
