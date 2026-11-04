package io.github.devkruidenier.landward.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import io.github.devkruidenier.landward.model.ChunkPosition;
import java.util.HashMap;
import java.util.Map;

public final class LandMapHolder implements InventoryHolder {

    enum Mode { PLAYER, ADMIN_PLAYER_CLAIMS, ADMIN_CLAIMS }

    private final Mode mode;
    private Inventory inventory;
    private final Map<Integer, ChunkPosition> chunksBySlot = new HashMap<>();

    public LandMapHolder() { this(Mode.PLAYER); }

    public LandMapHolder(Mode mode) { this.mode = mode; }

    boolean administrator() { return mode != Mode.PLAYER; }
    boolean administrativeClaims() { return mode == Mode.ADMIN_CLAIMS; }

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
