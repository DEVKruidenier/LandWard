package io.github.devkruidenier.landward.protection;

import java.util.function.BooleanSupplier;

/** A claim setting may restrict PvP, but cannot grant it over an external denial. */
public final class PvpAvailability {
    private PvpAvailability() {}

    public static boolean allowed(boolean worldAllowsPvp, BooleanSupplier regionBlocksPvp) {
        return worldAllowsPvp && !regionBlocksPvp.getAsBoolean();
    }
}
