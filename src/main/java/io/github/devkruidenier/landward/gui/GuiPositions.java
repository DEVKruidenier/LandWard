package io.github.devkruidenier.landward.gui;

import java.util.Map;

/** Logical action IDs mapped to content slots; the footer contains navigation only. */
final class GuiPositions {
    private GuiPositions() {}
    static int size(String profile, int original) {
        return switch (profile) {
            case "player:SETTINGS", "admin:ADMIN_SETTINGS" -> 36;
            case "player:MEMBER_MAIN" -> 18;
            default -> original;
        };
    }
    static Map<Integer, Integer> overrides(String profile) {
        return switch (profile) {
            case "player:SETTINGS" -> Map.ofEntries(
                    Map.entry(10, 1), Map.entry(11, 2), Map.entry(12, 3), Map.entry(13, 4),
                    Map.entry(19, 10), Map.entry(20, 11), Map.entry(21, 12),
                    Map.entry(23, 14), Map.entry(24, 15),
                    Map.entry(37, 19), Map.entry(40, 22), Map.entry(43, 25));
            case "admin:ADMIN_SETTINGS" -> Map.of(10, 1, 11, 2, 12, 3, 13, 4,
                    19, 11, 20, 14, 21, 15, 37, 19, 40, 22);
            case "player:WELCOME" -> Map.of(13, 11, 22, 13);
            case "player:MEMBER", "admin:ADMIN_MEMBER" -> Map.of(22, 7);
            case "admin:MAIN" -> Map.of(9, 10, 11, 12, 13, 14, 15, 4, 17, 16, 20, 8);
            case "admin:PLAYER_CLAIM_DETAILS" -> Map.of(18, 4, 20, 8);
            default -> Map.of();
        };
    }
}
