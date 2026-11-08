package io.github.devkruidenier.landward.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import io.github.devkruidenier.landward.model.ChunkPosition;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LandMapHolder implements InventoryHolder {

    enum Mode { PLAYER, ADMIN_PLAYER_CLAIMS, ADMIN_CLAIMS }

    private final Mode mode;
    private final UUID restrictedLandId;
    private Inventory inventory;
    private final Map<Integer, ChunkPosition> chunksBySlot = new HashMap<>();

    public LandMapHolder() { this(Mode.PLAYER, null); }

    public LandMapHolder(Mode mode) { this(mode, null); }

    public LandMapHolder(Mode mode, UUID restrictedLandId) {
        this.mode = mode;
        this.restrictedLandId = restrictedLandId;
    }

    boolean administrator() { return mode != Mode.PLAYER; }
    boolean administrativeClaims() { return mode == Mode.ADMIN_CLAIMS; }
    UUID restrictedLandId() { return restrictedLandId; }

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
