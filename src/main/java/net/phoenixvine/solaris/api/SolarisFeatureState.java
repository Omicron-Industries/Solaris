package net.phoenixvine.solaris.api;

public enum SolarisFeatureState {

    DISABLED,
    VISIBLE,
    ENABLED;

    public boolean atLeast(SolarisFeatureState minimum) {
        return ordinal() >= minimum.ordinal();
    }
}
