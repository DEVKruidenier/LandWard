package io.github.devkruidenier.landward.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

/** Releases page callbacks when an inventory closes or a player disconnects. */
public final class GuiCleanupListener implements Listener {
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        GuiLayout.forget(event.getView().getTopInventory());
    }
}
