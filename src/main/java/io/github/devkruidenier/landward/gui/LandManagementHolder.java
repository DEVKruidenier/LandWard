package io.github.devkruidenier.landward.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class LandManagementHolder implements InventoryHolder {
    enum Screen { WELCOME, MAIN, MEMBERS, RIGHTS }
    private final Screen screen;
    private final UUID memberId;
    private final Map<Integer, UUID> playersBySlot = new HashMap<>();
    private Inventory inventory;

    LandManagementHolder(Screen screen, UUID memberId) { this.screen = screen; this.memberId = memberId; }
    Screen screen() { return screen; }
    UUID memberId() { return memberId; }
    Map<Integer, UUID> playersBySlot() { return playersBySlot; }
    void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public @NotNull Inventory getInventory() { if (inventory == null) throw new IllegalStateException("Inventory not initialized"); return inventory; }
}
