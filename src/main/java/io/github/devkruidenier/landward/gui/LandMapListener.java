package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.command.LandCommand;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.model.LandType;
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
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (GuiLayout.navigation(event)) return;
        if (holder.administrator() && !player.hasPermission("landward.admin")) { player.closeInventory(); return; }
        int slot = GuiLayout.action(event.getView().getTopInventory(), event.getRawSlot());
        if (slot == 53) {
            if (holder.administrator() && holder.restrictedLandId() != null) {
                new ClaimAdminGui(plugin, landManager).openPlayerClaimDetails(player, holder.restrictedLandId());
            } else if (holder.administrator()) new ClaimAdminGui(plugin, landManager).openMain(player);
            else new LandManagementGui(plugin, landManager).openMain(player);
            return;
        }
        if (!canUseMap(player, holder)) return;
        if (slot == 47 || slot == 48 || slot == 50 || slot == 51) {
            new LandMapGui(plugin, landManager).move(player, holder,
                    slot == 47 ? -1 : slot == 51 ? 1 : 0,
                    slot == 48 ? -1 : slot == 50 ? 1 : 0);
            return;
        }
        ChunkPosition position = holder.chunksBySlot().get(slot);
        if (position == null || plugin.storage() == null) return;
        if (holder.administrator() && !canEditWorld(player, position)) return;
        var existingLand = landManager.landAt(position);
        if (existingLand.isPresent()) {
            if (holder.administrator()) {
                boolean administrative = landManager.typeOf(existingLand.get()) == LandType.ADMIN;
                boolean matchesRestriction = holder.restrictedLandId() == null
                        || existingLand.get().id().equals(holder.restrictedLandId());
                if (administrative == holder.administrativeClaims() && matchesRestriction) {
                    new LandMapGui(plugin, landManager).openUnclaimConfirmation(player, position, true,
                            holder.administrativeClaims(), holder.restrictedLandId(), holder);
                }
            } else if (existingLand.get().ownerId().equals(player.getUniqueId())) {
                new LandMapGui(plugin, landManager).openUnclaimConfirmation(player, position, false, false, null, holder);
            }
            return;
        }
        if (holder.administrativeClaims()) {
            try {
                String configuredName = plugin.getConfig().getString("admin-claims.default-name", "").strip();
                String name = configuredName.isEmpty()
                        ? plugin.localeService().message(plugin.localeService().localeFor(player), "gui.admin-default-name")
                        : configuredName;
                landManager.claimAdministration(name, position);
                plugin.syncMapIntegrations();
                new LandMapGui(plugin, landManager).reopen(player, holder);
            } catch (StorageException exception) {
                player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "general.storage-error"));
            }
            return;
        }
        if (holder.administrator()) {
            if (holder.restrictedLandId() == null) return;
            try {
                landManager.adminClaimPlayerLand(holder.restrictedLandId(), position);
                plugin.syncMapIntegrations();
                new LandMapGui(plugin, landManager).reopen(player, holder);
            } catch (StorageException exception) {
                player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "general.storage-error"));
            }
            return;
        }
        try { command.claimFromMenu(player, position); new LandMapGui(plugin, landManager).reopen(player, holder); }
        catch (StorageException exception) { player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "general.storage-error")); }
    }

    private void handleUnclaimConfirmation(InventoryClickEvent event, UnclaimConfirmationHolder confirmation) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (GuiLayout.navigation(event)) return;
        if (confirmation.administrator() && !player.hasPermission("landward.admin")) { player.closeInventory(); return; }
        if (confirmation.returnMap() != null && !canUseMap(player, confirmation.returnMap())) return;
        int slot = GuiLayout.action(event.getView().getTopInventory(), event.getRawSlot());
        if (slot == 11) {
            if (confirmation.administrator() && !canEditWorld(player, confirmation.position())) return;
            try {
                if (confirmation.administrator()) {
                    var current = landManager.landAt(confirmation.position());
                    if (current.isPresent()
                            && landManager.isAdministrative(current.get()) == confirmation.administrativeClaims()
                            && (confirmation.restrictedLandId() == null || confirmation.restrictedLandId().equals(current.get().id()))) {
                        landManager.adminUnclaim(confirmation.position());
                        plugin.syncMapIntegrations();
                    }
                } else {
                    command.unclaimFromMenu(player, confirmation.position());
                }
            } catch (StorageException exception) {
                player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "general.storage-error"));
            }
        }
        if (slot == 11 || slot == 15 || slot == 26) {
            if (confirmation.returnMap() != null) {
                new LandMapGui(plugin, landManager).reopen(player, confirmation.returnMap()); return;
            }
            if (confirmation.administrator() && confirmation.administrativeClaims()) new LandMapGui(plugin, landManager).openAdminClaims(player);
            else if (confirmation.administrator() && confirmation.restrictedLandId() != null) {
                if (landManager.land(confirmation.restrictedLandId()).isPresent()) {
                    new LandMapGui(plugin, landManager).openAdmin(player, confirmation.restrictedLandId());
                } else {
                    new ClaimAdminGui(plugin, landManager).openPlayerClaims(player);
                }
            } else if (confirmation.administrator()) new LandMapGui(plugin, landManager).openAdmin(player);
            else new LandMapGui(plugin, landManager).open(player);
        }
    }

    private boolean canUseMap(Player player, LandMapHolder holder) {
        if (holder.viewport() == null) { player.closeInventory(); return false; }
        var position = holder.viewport().center();
        if (holder.administrator() && !canEditWorld(player, position)) return false;
        if (!holder.administrator() && (!position.worldId().equals(player.getWorld().getUID()) || !plugin.isLandWorld(player.getWorld()))) {
            player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "claim.world-disabled"));
            player.closeInventory(); return false;
        }
        int limit = plugin.getConfig().getInt("claims.map-navigation-limit", 30);
        if (!holder.viewport().equals(holder.viewport().move(0, 0, limit))) {
            new LandMapGui(plugin, landManager).reopen(player, holder); return false;
        }
        return true;
    }

    private boolean canEditWorld(Player player, ChunkPosition position) {
        if (!player.hasPermission("landward.admin")) { player.closeInventory(); return false; }
        org.bukkit.World target = org.bukkit.Bukkit.getWorld(position.worldId());
        if (target == null) {
            player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "admin.claim-no-teleport"));
            return false;
        }
        if (!plugin.isLandWorld(player.getWorld()) || !plugin.isLandWorld(target)) {
            player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "claim.world-disabled"));
            return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof LandMapHolder
                || event.getView().getTopInventory().getHolder() instanceof UnclaimConfirmationHolder) {
            event.setCancelled(true);
        }
    }

}
