package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.model.ChunkPosition;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Inventory identity and immutable target for the unclaim confirmation screen. */
public final class UnclaimConfirmationHolder implements InventoryHolder {
    private final ChunkPosition position;
    private final boolean administrator;
    private final boolean administrativeClaims;
    private Inventory inventory;

    public UnclaimConfirmationHolder(ChunkPosition position, boolean administrator, boolean administrativeClaims) {
        this.position = position;
        this.administrator = administrator;
        this.administrativeClaims = administrativeClaims;
    }

    public ChunkPosition position() {
        return position;
    }

    boolean administrator() { return administrator; }
    boolean administrativeClaims() { return administrativeClaims; }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) throw new IllegalStateException("Confirmation inventory has not been initialized");
        return inventory;
    }
}
