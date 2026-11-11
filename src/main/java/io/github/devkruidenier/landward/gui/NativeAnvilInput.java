package io.github.devkruidenier.landward.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.view.AnvilView;

/** Creates a native menu that receives rename packets on both Bukkit implementations. */
final class NativeAnvilInput {
    private NativeAnvilInput() {}

    static AnvilView create(Player player, String title) {
        // Bukkit.createInventory(ANVIL) opens CraftContainer on Spigot, which
        // has anvil-shaped slots but is not the native rename-packet handler.
        AnvilView view = MenuType.ANVIL.builder().checkReachable(false).build(player);
        // InventoryView's String title API is shared by Paper and Spigot;
        // their builder title signatures differ (Component versus String).
        view.setTitle(title);
        return view;
    }
}
