package net.phoenixvine.solaris.api;

/**
 * A richer alternative to a plain boolean feature gate, for features that have a meaningful
 * middle ground between "off" and "fully on" — e.g. the world map: {@code DISABLED} means it
 * can't be opened at all, {@code VISIBLE} means it can be opened and browsed but doesn't sample
 * new terrain, {@code ENABLED} means it does. Not every feature needs all three distinctions
 * (a minimap has no meaningful "visible but frozen" state, for instance — for those, treat
 * {@code VISIBLE} and {@code ENABLED} as equivalent and only distinguish against {@code
 * DISABLED}), but every feature that uses this type shares the same three values and ordering
 * rather than each inventing its own bespoke enum.
 *
 * Deliberately ordinal-ordered (declaration order matters) so {@link #atLeast} is a simple
 * ordinal comparison — "does this feature have at least VISIBLE access" reads the same way
 * regardless of which feature is asking.
 */
public enum SolarisFeatureState {

    DISABLED,
    VISIBLE,
    ENABLED;

    public boolean atLeast(SolarisFeatureState minimum) {
        return ordinal() >= minimum.ordinal();
    }
}
