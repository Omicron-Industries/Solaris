package net.phoenixvine.solaris.client.color;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public record ChunkKey(ResourceLocation dimension, int x, int z) {

    public static ChunkKey of(Level level, ChunkPos pos) {
        return new ChunkKey(level.dimension().location(), pos.x, pos.z);
    }

    public ChunkPos toChunkPos() {
        return new ChunkPos(x, z);
    }

    @Override
    public String toString() {
        return dimension + "@" + x + "," + z;
    }
}
