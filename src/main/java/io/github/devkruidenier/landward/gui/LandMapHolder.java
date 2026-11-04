package io.github.devkruidenier.landward.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import io.github.devkruidenier.landward.model.ChunkPosition;
import java.util.HashMap;
import java.util.Map;

public final class LandMapHolder implements InventoryHolder {

    private final boolean administrator;
    private Inventory inventory;
    private final Map<Integer, ChunkPosition> chunksBySlot = new HashMap<>();

    public LandMapHolder() { this(false); }

    public LandMapHolder(boolean administrator) { this.administrator = administrator; }

    boolean administrator() { return administrator; }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
    Map<Integer, ChunkPosition> chunksBySlot() { return chunksBySlot; }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("Land map inventory has not been initialized");
        }
        return inventory;
    }
}
