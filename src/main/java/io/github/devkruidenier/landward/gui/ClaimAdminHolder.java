package io.github.devkruidenier.landward.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class ClaimAdminHolder implements InventoryHolder {
    enum Screen { MAIN, WORLDS, ADMIN_SETTINGS, ADMIN_ADD_MEMBERS, ADMIN_MEMBERS, ADMIN_MEMBER, PLAYER_CLAIMS, PLAYER_CLAIM_DETAILS, PLAYER_CLAIM_MEMBERS }

    private final Screen screen;
    private final Map<Integer, String> worldsBySlot = new HashMap<>();
    private final Map<Integer, UUID> playersBySlot = new HashMap<>();
    private final Map<Integer, UUID> landsBySlot = new HashMap<>();
    private UUID selectedLandId;
    private UUID selectedMemberId;
    private Inventory inventory;

    ClaimAdminHolder(Screen screen) { this.screen = screen; }
    Screen screen() { return screen; }
    Map<Integer, String> worldsBySlot() { return worldsBySlot; }
    Map<Integer, UUID> playersBySlot() { return playersBySlot; }
    Map<Integer, UUID> landsBySlot() { return landsBySlot; }
    UUID selectedLandId() { return selectedLandId; }
    void selectedLandId(UUID selectedLandId) { this.selectedLandId = selectedLandId; }
    UUID selectedMemberId() { return selectedMemberId; }
    void selectedMemberId(UUID selectedMemberId) { this.selectedMemberId = selectedMemberId; }
    void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public @NotNull Inventory getInventory() {
        if (inventory == null) throw new IllegalStateException("Inventory not initialized");
        return inventory;
    }
}
