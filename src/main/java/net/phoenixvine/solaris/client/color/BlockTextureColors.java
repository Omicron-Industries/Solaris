package net.phoenixvine.solaris.client.color;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.phoenixvine.solaris.PhoenixSolaris;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockTextureColors {

    private static final Map<Block, Integer> CACHE = new ConcurrentHashMap<>();

    private static final Set<Block> WARNED_FALLBACK_BLOCKS = ConcurrentHashMap.newKeySet();

    private static final int FALLBACK_RGB = 0x808080;

    private BlockTextureColors() {}

    public static int get(BlockState state) {
        return getResult(state).rgb;
    }

    public static final class Result {

        public final int rgb;
        public final boolean fallback;

        private Result(int rgb, boolean fallback) {
            this.rgb = rgb;
            this.fallback = fallback;
        }
    }

    public static Result getResult(BlockState state) {
        Block block = state.getBlock();
        Integer cached = CACHE.get(block);
        if (cached != null) return new Result(cached, false);

        Integer computed = computeAverage(state);
        if (computed != null) {
            CACHE.put(block, computed);
            return new Result(computed, false);
        }
        if (WARNED_FALLBACK_BLOCKS.add(block)) {
            PhoenixSolaris.LOGGER.warn(
                    "[Solaris] Couldn't compute a texture-average color for {} — every chunk containing it will " +
                            "fall back to its vanilla MapColor (or flat gray, if it has none) until this resolves. " +
                            "If this keeps recurring for the same block across sessions, it's a persistent " +
                            "failure worth reporting, not a one-time startup timing race.",
                    block);
        }
        return new Result(FALLBACK_RGB, true);
    }

    private static Integer computeAverage(BlockState state) {
        try {
            BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            TextureAtlasSprite sprite = model.getParticleIcon();

            if (sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                return null;
            }
            int width = sprite.contents().width();
            int height = sprite.contents().height();

            long r = 0;
            long g = 0;
            long b = 0;
            long weight = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int abgr = sprite.getPixelRGBA(0, x, y);
                    int a = FastColor.ABGR32.alpha(abgr);
                    if (a == 0) continue;
                    r += FastColor.ABGR32.red(abgr) * a;
                    g += FastColor.ABGR32.green(abgr) * a;
                    b += FastColor.ABGR32.blue(abgr) * a;
                    weight += a;
                }
            }
            if (weight == 0) return null;
            return (int) (r / weight) << 16 | (int) (g / weight) << 8 | (int) (b / weight);
        } catch (Exception e) {

            return null;
        }
    }
}
