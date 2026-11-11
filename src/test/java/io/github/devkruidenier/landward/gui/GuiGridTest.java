package io.github.devkruidenier.landward.gui;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class GuiGridTest {
    @Test void everyPageHasUniqueContentSlotsAndASeparateFooter() {
        for (int count = 0; count <= 45; count++) {
            int size = GuiGrid.size(count);
            assertTrue(size >= 18 && size <= 54 && size % 9 == 0);
            var used = new HashSet<Integer>();
            for (int entry = 0; entry < count; entry++) {
                int slot = GuiGrid.slot(entry, count);
                assertTrue(slot >= 0 && slot < size - 9, "Entry overlaps navigation");
                assertTrue(used.add(slot), "Entry would overwrite another entry");
            }
        }
    }
    @Test void usesCompactSizesAndCentersSmallLists() {
        assertEquals(18, GuiGrid.size(0));
        assertEquals(18, GuiGrid.size(1));
        assertEquals(18, GuiGrid.size(9));
        assertEquals(27, GuiGrid.size(10));
        assertEquals(54, GuiGrid.size(45));
        assertEquals(4, GuiGrid.slot(0, 1));
        assertEquals(3, GuiGrid.slot(0, 3));
        assertEquals(5, GuiGrid.slot(2, 3));
        assertEquals(10, GuiGrid.slot(9, 15));
        assertEquals(15, GuiGrid.slot(14, 15));
    }
    @Test void rejectsEntriesOutsideThePage() {
        assertThrows(IllegalArgumentException.class, () -> GuiGrid.size(46));
        assertThrows(IllegalArgumentException.class, () -> GuiGrid.slot(0, 0));
        assertThrows(IllegalArgumentException.class, () -> GuiGrid.slot(2, 2));
    }
}
