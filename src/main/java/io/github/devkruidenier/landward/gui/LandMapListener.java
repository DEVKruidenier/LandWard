package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.command.LandCommand;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.storage.StorageException;
import org.bukkit.entity.Player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class LandMapListener implements Listener {
    private final LandWardPlugin plugin; private final LandCommand command; private final LandManager landManager;
    public LandMapListener(LandWardPlugin plugin, LandCommand command, LandManager landManager) { this.plugin = plugin; this.command = command; this.landManager = landManager; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof LandMapHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        ChunkPosition position = holder.chunksBySlot().get(event.getRawSlot());
        if (position == null || plugin.storage() == null || landManager.landAt(position).isPresent()) return;
        try { command.claimFromMenu(player, position); new LandMapGui(plugin, landManager).open(player); }
        catch (StorageException exception) { player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "general.storage-error")); }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof LandMapHolder) {
            event.setCancelled(true);
        }
    }

}
