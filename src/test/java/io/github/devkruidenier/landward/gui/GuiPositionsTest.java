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
        assertEquals(player.get(23), admin.get(20)); // Doors
        assertEquals(player.get(24), admin.get(21)); // Interactions
        assertEquals(player.get(37), admin.get(37)); // Add member
        assertEquals(player.get(40), admin.get(40)); // Members
    }
}
