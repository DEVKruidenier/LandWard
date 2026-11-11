package io.github.devkruidenier.landward.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Identifies one LandWard anvil session and retains only text received from
 * {@code AnvilView#getRenameText()}. It deliberately never reads an item's
 * display name: slot labels are presentation, not player input.
 */
final class RenameAnvilHolder implements InventoryHolder {
    private final boolean creating;
    private Inventory inventory;
    private String enteredName;

    RenameAnvilHolder(boolean creating) {
        this.creating = creating;
    }

    boolean creating() {
        return creating;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    void setEnteredName(String enteredName) {
        this.enteredName = enteredName;
    }

    String enteredName() {
        return enteredName;
    }

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("Anvil inventory has not been created yet");
        }
        return inventory;
    }
}
