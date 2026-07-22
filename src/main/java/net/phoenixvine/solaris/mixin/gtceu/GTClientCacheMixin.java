package net.phoenixvine.solaris.mixin.gtceu;

import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.phoenixvine.solaris.integration.gtceu.GtVeinRegistry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.gregtechceu.gtceu.integration.map.cache.client.GTClientCache", remap = false)
public class GTClientCacheMixin {

    @Inject(method = "addVein", at = @At("RETURN"))
    private void solaris$onAddVein(ResourceKey<Level> dim, int gridX, int gridZ, GeneratedVeinMetadata vein,
                                   CallbackInfoReturnable<Boolean> cir) {
        GtVeinRegistry.add(dim, vein);
    }
}
