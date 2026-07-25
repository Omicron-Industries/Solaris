package net.phoenixvine.solaris.integration.kubejs;

import net.minecraftforge.common.ForgeConfigSpec;
import net.phoenixvine.solaris.api.SolarisAPI;
import net.phoenixvine.solaris.api.SolarisFeatureState;
import net.phoenixvine.solaris.api.event.WaypointEvent;
import net.phoenixvine.solaris.client.overlay.SolarisOverlay;
import net.phoenixvine.solaris.client.render.UnexploredStyle;
import net.phoenixvine.solaris.client.waypoint.Waypoint;
import net.phoenixvine.solaris.config.SolarisConfig;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ClassFilter;

public class SolarisKubeJSPlugin extends KubeJSPlugin {

    @Override
    public void registerClasses(ScriptType type, ClassFilter filter) {
        filter.allow(SolarisAPI.class);
        filter.allow(SolarisAPI.ScriptOverlay.class);
        filter.allow(SolarisFeatureState.class);
        filter.allow(UnexploredStyle.class);
        filter.allow(Waypoint.class);
        filter.allow(SolarisOverlay.class);

        filter.allow(WaypointEvent.class);
        filter.allow(WaypointEvent.Added.class);
        filter.allow(WaypointEvent.Removed.class);
        filter.allow(WaypointEvent.Reached.class);

        filter.allow(SolarisConfig.class);
        filter.allow(ForgeConfigSpec.ConfigValue.class);
        filter.allow(ForgeConfigSpec.BooleanValue.class);
        filter.allow(ForgeConfigSpec.IntValue.class);
        filter.allow(ForgeConfigSpec.DoubleValue.class);
        filter.allow(ForgeConfigSpec.EnumValue.class);
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        event.add("SolarisAPI", SolarisAPI.class);
        event.add("SolarisFeatureState", SolarisFeatureState.class);
        event.add("UnexploredStyle", UnexploredStyle.class);
        event.add("SolarisConfig", SolarisConfig.class);
    }
}
