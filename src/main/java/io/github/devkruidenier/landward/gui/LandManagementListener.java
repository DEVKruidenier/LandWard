package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.MemberPermissions;
import io.github.devkruidenier.landward.storage.StorageException;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.UUID;

public final class LandManagementListener implements Listener {
    private final LandWardPlugin plugin; private final LandManager landManager; private final LandManagementGui gui; private final LandRenameListener renameListener;
    public LandManagementListener(LandWardPlugin plugin, LandManager landManager, LandRenameListener renameListener) { this.plugin = plugin; this.landManager = landManager; this.gui = new LandManagementGui(plugin, landManager); this.renameListener = renameListener; }
    @EventHandler(priority = EventPriority.HIGH) public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof LandManagementHolder holder)) return;
        event.setCancelled(true); if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        try { handle(player, holder, event.getRawSlot()); } catch (StorageException exception) { plugin.getLogger().warning("Could not save land GUI change: " + exception.getMessage()); player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "general.storage-error")); }
    }
    @EventHandler(priority = EventPriority.HIGH) public void onDrag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof LandManagementHolder) event.setCancelled(true); }
    private void handle(Player player, LandManagementHolder holder, int slot) throws StorageException {
        if (holder.screen() == LandManagementHolder.Screen.MAIN) { if (slot == 45) gui.openMembers(player); else if (slot == 47) renameListener.begin(player); else if (slot == 49) player.closeInventory(); else if (slot == 53) new LandMapGui(plugin, landManager).open(player); else { UUID member = holder.playersBySlot().get(slot); if (member != null) gui.openRights(player, member); } return; }
        if (holder.screen() == LandManagementHolder.Screen.MEMBERS) { if (slot == 49) { gui.openMain(player); return; } UUID target = holder.playersBySlot().get(slot); if (target != null) { landManager.addMember(player.getUniqueId(), target); plugin.refreshAnalyticsIntegration(); gui.openMain(player); } return; }
        if (slot == 49) { gui.openMain(player); return; }
        UUID member = holder.memberId(); if (member == null) return;
        if (slot == 53) { landManager.removeMember(player.getUniqueId(), member); plugin.refreshAnalyticsIntegration(); gui.openMain(player); return; }
        MemberPermissions.Permission permission = switch (slot) { case 20 -> MemberPermissions.Permission.BUILD; case 22 -> MemberPermissions.Permission.CONTAINERS; case 24 -> MemberPermissions.Permission.ENTITIES; default -> null; };
        if (permission != null) {
            var current = landManager.permissions(member, landManager.ownedBy(player.getUniqueId()).orElseThrow().id());
            boolean enabled = switch (permission) {
                case BUILD -> !current.build();
                case CONTAINERS -> !current.containers();
                case ENTITIES -> !current.entities();
            };
            landManager.updateMemberPermission(player.getUniqueId(), member, permission, enabled);
            plugin.refreshAnalyticsIntegration();
            gui.openRights(player, member);
        }
    }
}
