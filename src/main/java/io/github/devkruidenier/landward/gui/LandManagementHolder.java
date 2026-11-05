package io.github.devkruidenier.landward.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class LandManagementHolder implements InventoryHolder {
    enum Screen { WELCOME, MAIN, SETTINGS, MEMBERS, ADD_MEMBERS, DEFAULT_RIGHTS, MEMBER, MEMBER_LANDS, MEMBER_MAIN }
    private final Screen screen;
    private final UUID memberId;
    private UUID selectedLandId;
    private final Map<Integer, UUID> playersBySlot = new HashMap<>();
    private Inventory inventory;

    LandManagementHolder(Screen screen, UUID memberId) { this.screen = screen; this.memberId = memberId; }
    Screen screen() { return screen; }
    UUID memberId() { return memberId; }
    UUID selectedLandId() { return selectedLandId; }
    void selectedLandId(UUID selectedLandId) { this.selectedLandId = selectedLandId; }
    Map<Integer, UUID> playersBySlot() { return playersBySlot; }
    void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public @NotNull Inventory getInventory() { if (inventory == null) throw new IllegalStateException("Inventory not initialized"); return inventory; }
}
