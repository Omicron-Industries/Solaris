package net.phoenixvine.solaris.client.export;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.phoenixvine.solaris.client.color.ChunkColorSampler;
import net.phoenixvine.solaris.client.color.ChunkKey;
import net.phoenixvine.solaris.client.color.PersistentChunkStore;
import net.phoenixvine.solaris.client.waypoint.WaypointManager;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;

public final class SolarisWebExporter {

    private static final int MAGIC = 0x534F4C4D;
    private static final int VERSION = 1;
    private static final DateTimeFormatter EXPORT_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");

    private SolarisWebExporter() {}

    public static void exportCurrentDimension(Consumer<Component> messageConsumer) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        String dimension = mc.level.dimension().location().toString();
        if (!PersistentChunkStore.isLoaded(dimension)) {
            messageConsumer.accept(
                    Component.literal("§eMap data is still loading — wait a moment and try again."));
            return;
        }

        Map<ChunkKey, PersistentChunkStore.Entry> chunks = PersistentChunkStore.snapshot(dimension);
        if (chunks.isEmpty()) {
            messageConsumer.accept(Component.literal("§eNothing explored yet in this dimension to export."));
            return;
        }

        String worldName = friendlyWorldName();
        String safeWorld = worldName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String safeDimension = dimension.replaceAll("[^a-zA-Z0-9._-]", "_");
        String timestamp = LocalDateTime.now().format(EXPORT_NAME_FORMAT);
        String baseName = safeWorld + "_" + safeDimension + "_" + timestamp;

        File dir = new File(mc.gameDirectory, "solaris_maps");
        dir.mkdirs();
        File mapFile = new File(dir, baseName + ".solmap");
        File waypointsFile = new File(dir, baseName + ".waypoints.json");
        Path sourceWaypoints = WaypointManager.currentWaypointsFile();

        Util.ioPool().execute(() -> {
            try {
                writeSolmap(mapFile, worldName, dimension, chunks);

                boolean copiedWaypoints = false;
                if (sourceWaypoints != null && Files.exists(sourceWaypoints)) {
                    Files.copy(sourceWaypoints, waypointsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    copiedWaypoints = true;
                }

                Component mapLink = fileLink(mapFile);
                Component message = copiedWaypoints ?
                        Component.literal("§aExported ").append(mapLink)
                                .append(Component.literal(" + ")).append(fileLink(waypointsFile)) :
                        Component.literal("§aExported ").append(mapLink);
                messageConsumer.accept(message);
            } catch (IOException e) {
                messageConsumer.accept(Component.literal("§cCouldn't export web map: " + e.getMessage()));
            }
        });
    }

    private static Component fileLink(File file) {
        return Component.literal(file.getName()).withStyle(ChatFormatting.UNDERLINE).withStyle(
                style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.getAbsolutePath())));
    }

    private static void writeSolmap(File file, String worldName, String dimension,
                                    Map<ChunkKey, PersistentChunkStore.Entry> chunks) throws IOException {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (ChunkKey key : chunks.keySet()) {
            minX = Math.min(minX, key.x());
            minZ = Math.min(minZ, key.z());
            maxX = Math.max(maxX, key.x());
            maxZ = Math.max(maxZ, key.z());
        }

        try (DataOutputStream out = new DataOutputStream(
                new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(file.toPath()))))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeUTF(worldName);
            out.writeUTF(dimension);
            out.writeLong(System.currentTimeMillis());
            out.writeInt(chunks.size());
            out.writeInt(minX);
            out.writeInt(minZ);
            out.writeInt(maxX);
            out.writeInt(maxZ);

            for (Map.Entry<ChunkKey, PersistentChunkStore.Entry> e : chunks.entrySet()) {
                ChunkKey key = e.getKey();
                PersistentChunkStore.Entry entry = e.getValue();
                out.writeInt(key.x());
                out.writeInt(key.z());
                int[] pixels = entry.pixels();
                int[] waterTint = entry.waterTint();
                int[] waterDepth = entry.waterDepth();
                boolean[] waterOcean = entry.waterOcean();
                int[] heights = entry.heights();
                for (int i = 0; i < pixels.length; i++) {
                    int abgr = pixels[i];

                    if (waterTint[i] != 0) {
                        abgr = ChunkColorSampler.compositeWaterTint(waterTint[i], waterDepth[i], waterOcean[i],
                                heights[i]);
                    }

                    out.writeByte(abgr & 0xFF);
                    out.writeByte(abgr >> 8 & 0xFF);
                    out.writeByte(abgr >> 16 & 0xFF);
                }
                for (int height : heights) {
                    out.writeShort((short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, height)));
                }
            }
        }
    }

    private static String friendlyWorldName() {
        Minecraft mc = Minecraft.getInstance();
        ServerData server = mc.getCurrentServer();
        if (server != null) return server.name;
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            return mc.getSingleplayerServer().getWorldData().getLevelName();
        }
        return "world";
    }
}
