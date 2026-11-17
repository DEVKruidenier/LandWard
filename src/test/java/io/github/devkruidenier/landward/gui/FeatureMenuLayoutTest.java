package io.github.devkruidenier.landward.gui;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;
class FeatureMenuLayoutTest {
    @Test void environmentHasTwoAdjacentCenteredRowsAndAnEmptyRowBeforeNavigation() {
        var slots = new HashSet<Integer>();
        for (int i = 0; i < 10; i++) assertTrue(slots.add(FeatureMenuLayout.environmentSlot(i)));
        assertEquals(java.util.Set.of(2, 3, 4, 5, 6, 11, 12, 13, 14, 15), slots);
        assertTrue(slots.stream().noneMatch(slot -> slot >= 18));
    }
    @Test void everyWarpPageFitsAboveFooterAndCentersItsPartialFinalRow() {
        for (int count = 1; count <= 45; count++) {
            var slots = new HashSet<Integer>();
            for (int i = 0; i < count; i++) {
                int slot = FeatureMenuLayout.listSlot(i, count);
                assertTrue(slot < 45); assertTrue(slots.add(slot));
            }
            for (int row = 0; row <= (count - 1) / 9; row++) {
                final int r = row;
                var values = slots.stream().filter(slot -> slot / 9 == r).sorted().toList();
                assertTrue(Math.abs(values.getFirst() % 9 - (8 - values.getLast() % 9)) <= 1);
                assertEquals(values.size() - 1, values.getLast() - values.getFirst());
            }
        }
    }
}
