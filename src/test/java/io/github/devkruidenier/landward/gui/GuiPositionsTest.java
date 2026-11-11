package io.github.devkruidenier.landward.gui;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GuiPositionsTest {
    @Test void allSettingsAndManagementActionsRemainAboveNavigationWithNoCollisions() {
        Map<String, List<Integer>> screens = Map.ofEntries(
                Map.entry("player:SETTINGS", List.of(10,11,12,13,19,20,21,23,24,37,40,43)),
                Map.entry("admin:ADMIN_SETTINGS", List.of(10,11,12,13,19,20,21,37,40)),
                Map.entry("player:MAIN", List.of(4,11,13,15,17)),
                Map.entry("player:WELCOME", List.of(13,15,22)),
                Map.entry("player:MEMBER", List.of(4,10,13,16,22)),
                Map.entry("admin:ADMIN_MEMBER", List.of(4,10,13,16,22)),
                Map.entry("admin:MAIN", List.of(9,11,13,15,17,20)),
                Map.entry("admin:PLAYER_CLAIM_DETAILS", List.of(10,12,14,16,18,20)),
                Map.entry("confirmation", List.of(11,15)));
        screens.forEach((screen, sources) -> {
            int size = GuiPositions.size(screen, 27);
            Set<Integer> used = new HashSet<>();
            for (int source : sources) {
                int slot = GuiPositions.overrides(screen).getOrDefault(source, source);
                assertTrue(slot >= 0 && slot < size - 9, screen + " has a regular action in navigation");
                assertTrue(used.add(slot), screen + " overwrites a button");
            }
        });
    }
    @Test void sharedPlayerAndSafeZoneControlsHaveTheSameLocations() {
        var player = GuiPositions.overrides("player:SETTINGS");
        var admin = GuiPositions.overrides("admin:ADMIN_SETTINGS");
        for (int combat = 10; combat <= 13; combat++) assertEquals(player.get(combat), admin.get(combat));
        assertEquals(player.get(20), admin.get(19)); // Containers
        assertEquals(player.get(24) - player.get(23), admin.get(21) - admin.get(20));
        assertEquals(player.get(37), admin.get(37)); // Add member
        assertEquals(player.get(40), admin.get(40)); // Members
    }
    @Test void everyRenderedContentRowIsAdjacentAndCenteredWhileNavigationStaysFixed() {
        for (String profile : List.of("player:MAIN", "player:SETTINGS", "admin:MAIN",
                "admin:ADMIN_SETTINGS", "player:MEMBER", "admin:PLAYER_CLAIM_DETAILS")) {
            int size = GuiPositions.size(profile, 27);
            Map<Integer, Integer> positions = new HashMap<>(GuiPositions.overrides(profile));
            positions.put(53, size - 9);
            positions.put(54, size - 1);
            Map<Integer, Integer> rendered = GuiPositions.center(positions, positions.keySet(), size);
            assertEquals(size - 9, rendered.get(53));
            assertEquals(size - 1, rendered.get(54));
            for (int row = 0; row < size / 9 - 1; row++) {
                final int start = row * 9;
                var slots = rendered.values().stream().filter(slot -> slot >= start && slot < start + 9).sorted().toList();
                for (int i = 0; i < slots.size(); i++)
                    assertEquals(start + (9 - slots.size()) / 2 + i, slots.get(i), profile);
            }
        }
    }
    @Test void optionalMissingButtonsDoNotLeaveHoles() {
        Map<Integer, Integer> positions = Map.of(13, 11, 22, 13, 15, 15, 26, 18);
        var rendered = GuiPositions.center(positions, Set.of(13,22,26), 27);
        assertEquals(12, rendered.get(13));
        assertEquals(13, rendered.get(22));
        assertEquals(18, rendered.get(26));
        assertFalse(rendered.containsKey(15));
    }
}
