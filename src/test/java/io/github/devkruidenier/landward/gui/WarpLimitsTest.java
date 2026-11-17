package io.github.devkruidenier.landward.gui;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class WarpLimitsTest {
    @Test void exactNumericNodesOverrideFallbackAndOnlyExplicitUnlimitedIsUnlimited() {
        assertEquals(3, WarpLimits.resolve(Set.of("*", "landward.*", "landward.warps.*"), 3));
        assertEquals(1, WarpLimits.resolve(Set.of("landward.warps.1"), 3));
        assertEquals(50, WarpLimits.resolve(Set.of("landward.warps.5", "landward.warps.50"), 3));
        assertEquals(-1, WarpLimits.resolve(Set.of("landward.warps.unlimited"), 3));
        assertEquals(3, WarpLimits.resolve(Set.of("landward.warps.-1", "landward.warps.99999999999999"), 3));
        assertEquals(0, WarpLimits.resolve(Set.of("landward.warps.0"), 3));
    }
}
