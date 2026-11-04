package io.github.devkruidenier.landward.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionComparatorTest {

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
