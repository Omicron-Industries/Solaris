package net.phoenixvine.solaris.client.color;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Solaris's own block color source: an alpha-weighted average of a block's actual particle-break
 * texture (the same texture/sprite vanilla already ships and uses for break particles), computed
 * once per {@link Block} and cached here in memory only — no new assets, no disk writes, so this
 * costs nothing in file size and next to nothing at runtime (one small texture scan per distinct
 * block ever sampled, not per pixel/chunk). {@link ChunkColorSampler} uses this in place of
 * vanilla's {@link net.minecraft.world.level.material.MapColor} for any block that reaches its
 * generic fallback branch — MapColor packs 1000+ blocks into a few dozen hand-picked colors from
 * a palette designed for a fixed, decade-old vanilla map item; reading each block's own texture
 * gives it a real, distinct color for free instead.
 *
 * {@link TextureAtlasSprite#getPixelRGBA} reads straight from the sprite's own original image
 * (verified against decompiled {@code TextureAtlasSprite.java}: {@code
 * this.contents.getOriginalImage().getPixelRGBA(x, y)}), not the stitched atlas — so no atlas
 * -space offset math is needed, and the result is {@code NativeImage}'s usual ABGR packing, same
 * as everywhere else in this codebase that reads pixels directly (see {@code SolarisTexture
 * #blurWater} for the same convention).
 */
public final class BlockTextureColors {

    private static final Map<Block, Integer> CACHE = new ConcurrentHashMap<>();

    /** Fallback for a block whose particle icon is fully transparent or fails to resolve — a neutral mid-gray. */
    private static final int FALLBACK_RGB = 0x808080;

    private BlockTextureColors() {}

    /** Returns a plain {@code 0xRRGGBB} color (no alpha) — same convention {@link BlockColorOverrides} uses. */
    public static int get(BlockState state) {
        return CACHE.computeIfAbsent(state.getBlock(), block -> computeAverage(state));
    }

    private static int computeAverage(BlockState state) {
        try {
            BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            TextureAtlasSprite sprite = model.getParticleIcon();
            // Blocks whose real appearance comes entirely from a BlockEntityRenderer (chests,
            // shulker boxes, banners, beds, etc. — no ordinary quad-based baked model) don't
            // throw here; getParticleIcon() happily hands back Mojang's own placeholder sprite
            // instead (the black/magenta "missing texture" checkerboard). Averaging that sprite
            // "succeeds" and produces a real, solid color — just the wrong one, a garish
            // purple/cyan tile that reads nothing like the actual block. Detect it by name and
            // fall back the same as an outright failure, rather than silently painting it.
            if (sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                return FALLBACK_RGB;
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
            if (weight == 0) return FALLBACK_RGB;
            return (int) (r / weight) << 16 | (int) (g / weight) << 8 | (int) (b / weight);
        } catch (Exception e) {
            // A handful of blocks (fluids, some modded models) don't resolve a normal particle
            // icon the way this expects — fall back rather than let one bad block break the
            // whole chunk's sampling pass.
            return FALLBACK_RGB;
        }
    }
}
