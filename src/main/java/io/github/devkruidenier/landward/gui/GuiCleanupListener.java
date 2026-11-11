package io.github.devkruidenier.landward.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Releases page callbacks when an inventory closes or a player disconnects. */
public final class GuiCleanupListener implements Listener {
    @EventHandler
    public void onQuit(PlayerQuitEvent event) { GuiLayout.forgetPlayer(event.getPlayer().getUniqueId()); }
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        GuiLayout.forget(event.getView().getTopInventory());
    }
}
