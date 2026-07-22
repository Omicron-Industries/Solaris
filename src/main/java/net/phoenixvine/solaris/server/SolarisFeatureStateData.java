package net.phoenixvine.solaris.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.phoenixvine.solaris.api.SolarisFeatureState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class SolarisFeatureStateData extends SavedData {

    private static final String SAVE_KEY = "solaris_feature_state";

    private final Map<UUID, Map<ResourceLocation, Map<String, SolarisFeatureState>>> byToken = new LinkedHashMap<>();

    public static SolarisFeatureStateData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(SolarisFeatureStateData::load, SolarisFeatureStateData::new,
                SAVE_KEY);
    }

    private static SolarisFeatureStateData load(CompoundTag tag) {
        SolarisFeatureStateData data = new SolarisFeatureStateData();
        for (String tokenKey : tag.getAllKeys()) {
            UUID token = UUID.fromString(tokenKey);
            CompoundTag dimensions = tag.getCompound(tokenKey);
            Map<ResourceLocation, Map<String, SolarisFeatureState>> perDimension = new LinkedHashMap<>();
            for (String dimensionKey : dimensions.getAllKeys()) {
                CompoundTag features = dimensions.getCompound(dimensionKey);
                Map<String, SolarisFeatureState> perFeature = new LinkedHashMap<>();
                for (String featureId : features.getAllKeys()) {
                    try {
                        perFeature.put(featureId, SolarisFeatureState.valueOf(features.getString(featureId)));
                    } catch (IllegalArgumentException ignored) {

                    }
                }
                perDimension.put(new ResourceLocation(dimensionKey), perFeature);
            }
            data.byToken.put(token, perDimension);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        for (Map.Entry<UUID, Map<ResourceLocation, Map<String, SolarisFeatureState>>> tokenEntry : byToken
                .entrySet()) {
            CompoundTag dimensions = new CompoundTag();
            for (Map.Entry<ResourceLocation, Map<String, SolarisFeatureState>> dimensionEntry : tokenEntry.getValue()
                    .entrySet()) {
                CompoundTag features = new CompoundTag();
                for (Map.Entry<String, SolarisFeatureState> featureEntry : dimensionEntry.getValue().entrySet()) {
                    features.putString(featureEntry.getKey(), featureEntry.getValue().name());
                }
                dimensions.put(dimensionEntry.getKey().toString(), features);
            }
            tag.put(tokenEntry.getKey().toString(), dimensions);
        }
        return tag;
    }

    public SolarisFeatureState get(UUID token, ResourceLocation dimension, String featureId) {
        Map<ResourceLocation, Map<String, SolarisFeatureState>> perDimension = byToken.get(token);
        if (perDimension == null) return SolarisFeatureState.ENABLED;
        Map<String, SolarisFeatureState> perFeature = perDimension.get(dimension);
        if (perFeature == null) return SolarisFeatureState.ENABLED;
        return perFeature.getOrDefault(featureId, SolarisFeatureState.ENABLED);
    }

    public void set(UUID token, ResourceLocation dimension, String featureId, SolarisFeatureState state) {
        byToken.computeIfAbsent(token, t -> new LinkedHashMap<>())
                .computeIfAbsent(dimension, d -> new LinkedHashMap<>())
                .put(featureId, state);
        setDirty();
    }

    public Map<String, Map<String, SolarisFeatureState>> snapshot(UUID token) {
        Map<String, Map<String, SolarisFeatureState>> out = new LinkedHashMap<>();
        Map<ResourceLocation, Map<String, SolarisFeatureState>> perDimension = byToken.get(token);
        if (perDimension == null) return out;
        for (Map.Entry<ResourceLocation, Map<String, SolarisFeatureState>> entry : perDimension.entrySet()) {
            out.put(entry.getKey().toString(), new LinkedHashMap<>(entry.getValue()));
        }
        return out;
    }
}
