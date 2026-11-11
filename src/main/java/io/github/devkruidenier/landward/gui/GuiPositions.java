package io.github.devkruidenier.landward.gui;

import java.util.Map;
import java.util.Set;
import java.util.HashMap;

/** Logical action IDs mapped to content slots; the footer contains navigation only. */
final class GuiPositions {
    private GuiPositions() {}
    /** Pack every content row into one consecutive, horizontally centred group. */
    static Map<Integer, Integer> center(Map<Integer, Integer> positions, Set<Integer> present, int size) {
        Map<Integer, Integer> result = new HashMap<>();
        positions.forEach((source, slot) -> { if (present.contains(source)) result.put(source, slot); });
        for (int row = 0; row < size / 9 - 1; row++) {
            int start = row * 9;
            var entries = positions.entrySet().stream()
                    .filter(entry -> present.contains(entry.getKey()) && entry.getValue() >= start && entry.getValue() < start + 9)
                    .sorted(Map.Entry.comparingByValue()).toList();
            int first = start + (9 - entries.size()) / 2;
            for (int index = 0; index < entries.size(); index++) result.put(entries.get(index).getKey(), first + index);
        }
        return result;
    }
    static int size(String profile, int original) {
        return switch (profile) {
            case "player:SETTINGS", "admin:ADMIN_SETTINGS" -> 27;
            case "player:MEMBER_MAIN" -> 18;
            default -> original;
        };
    }
    static Map<Integer, Integer> overrides(String profile) {
        return switch (profile) {
            case "player:MAIN" -> Map.of(4, 4, 11, 11, 13, 12, 15, 13, 17, 22);
            case "player:SETTINGS" -> Map.ofEntries(
                    Map.entry(10, 0), Map.entry(19, 1), Map.entry(20, 2),
                    Map.entry(23, 3), Map.entry(24, 4), Map.entry(21, 5),
                    Map.entry(12, 6), Map.entry(11, 7), Map.entry(13, 8),
                    Map.entry(37, 21), Map.entry(40, 22), Map.entry(43, 23));
            case "admin:ADMIN_SETTINGS" -> Map.ofEntries(
                    Map.entry(10, 0), Map.entry(14, 1), Map.entry(19, 2),
                    Map.entry(20, 3), Map.entry(21, 4), Map.entry(15, 5),
                    Map.entry(12, 6), Map.entry(11, 7), Map.entry(13, 8),
                    Map.entry(37, 21), Map.entry(40, 22));
            case "player:WELCOME" -> Map.of(13, 11, 22, 13);
            case "player:MEMBER", "admin:ADMIN_MEMBER" -> Map.of(4, 11, 10, 12, 13, 13, 16, 14, 22, 15);
            case "admin:MAIN" -> Map.of(9, 3, 11, 4, 13, 5, 17, 21, 15, 23, 20, 22);
            case "admin:PLAYER_CLAIM_DETAILS" -> Map.of(10, 10, 12, 11, 14, 12, 16, 13, 18, 14, 20, 15);
            default -> Map.of();
        };
    }
}
