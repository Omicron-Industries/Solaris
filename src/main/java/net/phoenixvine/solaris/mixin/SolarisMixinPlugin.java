package net.phoenixvine.solaris.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Skips mixins that target an optional soft-dependency's classes when that mod isn't
 * installed — right now, just the {@code mixin.gtceu.*} mixins, which target GTCEu classes
 * that only exist on a client with GTCEu installed. Without this, the mixin transformer would
 * try (and fail) to locate those classes on any client that doesn't have GTCEu at all.
 *
 * Deliberately does NOT use {@code ModList.get().isLoaded(...)} — mixin config plugins run
 * during early class transformation, well before FML has actually built {@code ModList}
 * (confirmed by a real crash: {@code ModList.get()} returned null here at boot). A raw
 * classpath resource lookup for one of GTCEu's own classes works at this stage instead, since
 * it only depends on the jar being on the classpath at all, not on FML's mod-loading state.
 */
public class SolarisMixinPlugin implements IMixinConfigPlugin {

    private static final String GTCEU_PROBE_CLASS = "com/gregtechceu/gtceu/integration/map/cache/client/GTClientCache.class";

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.startsWith("net.phoenixvine.solaris.mixin.gtceu.")) {
            return SolarisMixinPlugin.class.getClassLoader().getResource(GTCEU_PROBE_CLASS) != null;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                         IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                          IMixinInfo mixinInfo) {}
}
