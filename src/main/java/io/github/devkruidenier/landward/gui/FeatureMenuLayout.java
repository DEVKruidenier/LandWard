package io.github.devkruidenier.landward.gui;

final class FeatureMenuLayout {
    private FeatureMenuLayout() {}
    static int environmentSlot(int index) {
        if (index < 0 || index >= 10) throw new IllegalArgumentException("Invalid environment index");
        return index / 5 * 9 + 2 + index % 5;
    }
    static int listSlot(int index, int count) {
        if (count < 1 || count > 45 || index < 0 || index >= count) throw new IllegalArgumentException("Invalid page index");
        int rowSize = Math.min(9, count - index / 9 * 9);
        return index / 9 * 9 + (9 - rowSize) / 2 + index % 9;
    }
}
