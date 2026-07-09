package net.phoenixvine.solaris.mixin.gtceu;

import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.phoenixvine.solaris.integration.gtceu.GtVeinRegistry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mirrors every vein GTCEu's client-side vein cache accepts into Solaris's own
 * {@link GtVeinRegistry}, instead of relying on GTCEu's own (broken for this purpose)
 * {@code getVeinsInArea} query — see {@link GtVeinRegistry}'s class doc for why. {@code addVein}
 * is the single funnel every reveal path (prospecting, and reloading GTCEu's own persisted
 * client cache on rejoin) already goes through, so tapping it here catches all of them.
 * Only ever applied when GTCEu is actually installed — see {@code SolarisMixinPlugin}.
 *
 * Deliberately targets the class by string ({@code targets = ...}) rather than
 * {@code value = GTClientCache.class} — a class-literal reference bakes a hard bytecode
 * reference to GTCEu's class into this mixin regardless of whether the plugin skips applying
 * it, which risks a load-time error on a client without GTCEu. The annotation processor's
 * "should be specified in value" note is expected and intentionally not followed here.
 */
@Mixin(targets = "com.gregtechceu.gtceu.integration.map.cache.client.GTClientCache", remap = false)
public class GTClientCacheMixin {

    @Inject(method = "addVein", at = @At("RETURN"))
    private void solaris$onAddVein(ResourceKey<Level> dim, int gridX, int gridZ, GeneratedVeinMetadata vein,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            GtVeinRegistry.add(dim, vein);
        }
    }
}
