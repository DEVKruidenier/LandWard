package io.github.devkruidenier.landward.protection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class PvpAvailabilityTest {
    @Test
    void worldDenialAlwaysWinsAndDoesNotQueryAnOptionalPlugin() {
        assertFalse(PvpAvailability.allowed(false, () -> {
            fail("A world denial must short-circuit the optional integration");
            return false;
        }));
    }

    @Test
    void worldGuardDenialWinsEvenWhenTheWorldAllowsPvp() {
        assertFalse(PvpAvailability.allowed(true, () -> true));
    }

    @Test
    void controlIsAvailableOnlyWhenBothSourcesAllowPvp() {
        assertTrue(PvpAvailability.allowed(true, () -> false));
        assertFalse(PvpAvailability.allowed(false, () -> true));
        assertFalse(PvpAvailability.allowed(false, () -> false));
    }
}
