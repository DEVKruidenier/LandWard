package io.github.devkruidenier.landward.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Identifies LandWard's rename anvil without relying on its display title. */
final class RenameAnvilHolder implements InventoryHolder {
    private final boolean creating;
    private Inventory inventory;

    RenameAnvilHolder(boolean creating) {
        this.creating = creating;
    }

    boolean creating() {
        return creating;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) throw new IllegalStateException("Rename anvil has not been initialized");
        return inventory;
    }
}
