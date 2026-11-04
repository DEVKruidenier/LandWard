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
        if (event.getView().getTopInventory().getHolder() instanceof UnclaimConfirmationHolder confirmation) {
            handleUnclaimConfirmation(event, confirmation);
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof LandMapHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (event.getRawSlot() == 53) {
            if (holder.administrator()) new ClaimAdminGui(plugin, landManager).openMain(player);
            else new LandManagementGui(plugin, landManager).openMain(player);
            return;
        }
        ChunkPosition position = holder.chunksBySlot().get(event.getRawSlot());
        if (position == null || plugin.storage() == null) return;
        var existingLand = landManager.landAt(position);
        if (existingLand.isPresent()) {
            if (holder.administrator()) {
                new LandMapGui(plugin, landManager).openUnclaimConfirmation(player, position, true);
            } else if (existingLand.get().ownerId().equals(player.getUniqueId())) {
                new LandMapGui(plugin, landManager).openUnclaimConfirmation(player, position, false);
            }
            return;
        }
        try { command.claimFromMenu(player, position); new LandMapGui(plugin, landManager).open(player); }
        catch (StorageException exception) { player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "general.storage-error")); }
    }

    private void handleUnclaimConfirmation(InventoryClickEvent event, UnclaimConfirmationHolder confirmation) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (event.getRawSlot() == 11) {
            try {
                if (confirmation.administrator()) {
                    landManager.adminUnclaim(confirmation.position());
                    plugin.syncMapIntegrations();
                } else {
                    command.unclaimFromMenu(player, confirmation.position());
                }
            } catch (StorageException exception) {
                player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "general.storage-error"));
            }
        }
        if (event.getRawSlot() == 11 || event.getRawSlot() == 15 || event.getRawSlot() == 22) {
            if (confirmation.administrator()) new LandMapGui(plugin, landManager).openAdmin(player);
            else new LandMapGui(plugin, landManager).open(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof LandMapHolder
                || event.getView().getTopInventory().getHolder() instanceof UnclaimConfirmationHolder) {
            event.setCancelled(true);
        }
    }

}
