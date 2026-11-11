package io.github.devkruidenier.landward.gui;

/** Pure list layout rules shared by every paged inventory. Footer has its own row. */
final class GuiGrid {
    private GuiGrid() {}
    static int pages(int total) {
        if (total < 0) throw new IllegalArgumentException("Negative entry count");
        return Math.max(1, (total + 44) / 45);
    }
    static int page(int requested, int total) { return Math.max(0, Math.min(requested, pages(total) - 1)); }
    static int start(int requested, int total) { return page(requested, total) * 45; }
    static int count(int requested, int total) { return Math.min(45, total - start(requested, total)); }
    static int size(int count) {
        if (count < 0 || count > 45) throw new IllegalArgumentException("Page entry count out of range");
        return Math.max(18, ((count + 8) / 9 + 1) * 9);
    }
    static int slot(int index, int count) {
        if (index < 0 || index >= count) throw new IllegalArgumentException("Invalid entry index");
        return count <= 7 ? (9 - count) / 2 + index : index;
    }
}
