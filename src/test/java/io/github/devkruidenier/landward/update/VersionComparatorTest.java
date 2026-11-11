package io.github.devkruidenier.landward.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionComparatorTest {
    @Test
    void comparesTheFourthLandWardComponentAndBetaNumbers() {
        assertTrue(VersionComparator.isNewer("1.0.1.6", "1.0.1.5"));
        assertFalse(VersionComparator.isNewer("1.0.1.5", "1.0.1.6"));
        assertFalse(VersionComparator.isNewer("1.0.1.6", "1.0.1.6"));
        assertTrue(VersionComparator.isNewer("1.0.1.6", "1.0.1.6-beta12"));
        assertFalse(VersionComparator.isNewer("1.0.1.6-beta12", "1.0.1.6"));
        assertTrue(VersionComparator.isNewer("1.0.2.0-beta10", "1.0.2.0-beta2"));
        assertFalse(VersionComparator.isNewer("1.0.1.6", "1.0.2.0-beta1"));
    }

    @Test
    void detectsNewerSemanticVersion() {
        assertTrue(VersionComparator.isNewer("v1.2.0", "1.1.9"));
        assertTrue(VersionComparator.isNewer("0.2.0", "0.1.0-SNAPSHOT"));
    }

    @Test
    void acceptsCurrentOrOlderVersion() {
        assertFalse(VersionComparator.isNewer("v0.1.0", "0.1.0-SNAPSHOT"));
        assertFalse(VersionComparator.isNewer("0.0.9", "0.1.0"));
    }
}
