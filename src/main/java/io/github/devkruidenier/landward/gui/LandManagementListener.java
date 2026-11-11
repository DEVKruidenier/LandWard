package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.MemberPermissions;
import io.github.devkruidenier.landward.model.LandProtectionSettings;
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
    public LandManagementListener(LandWardPlugin plugin, LandManager landManager, LandRenameListener renameListener) {
        this.plugin = plugin;
        this.landManager = landManager;
        this.gui = new LandManagementGui(plugin, landManager);
        this.renameListener = renameListener;
    }
    @EventHandler(priority = EventPriority.HIGH) public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof LandManagementHolder holder)) return;
        event.setCancelled(true); if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (GuiLayout.navigation(event)) return;
        try { handle(player, holder, GuiLayout.action(event.getView().getTopInventory(), event.getRawSlot())); } catch (StorageException exception) { plugin.getLogger().warning("Could not save land GUI change: " + exception.getMessage()); player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "general.storage-error")); }
    }
    @EventHandler(priority = EventPriority.HIGH) public void onDrag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof LandManagementHolder) event.setCancelled(true); }
    private void handle(Player player, LandManagementHolder holder, int slot) throws StorageException {
        if (holder.screen() == LandManagementHolder.Screen.WELCOME) {
            if (slot == 13) renameListener.beginCreate(player);
            else if (slot == 22) new LandMapGui(plugin, landManager).open(player);
            else if (slot == 15) {
                var memberLands = landManager.memberLands(player.getUniqueId());
                if (memberLands.size() == 1) gui.openMemberMain(player, memberLands.getFirst());
                else if (!memberLands.isEmpty()) gui.openMemberLands(player, memberLands);
            }
            return;
        }
        if (holder.screen() == LandManagementHolder.Screen.MEMBER_MAIN) {
            if (slot == 26) player.closeInventory();
            return;
        }
        if (holder.screen() == LandManagementHolder.Screen.MEMBER_LANDS) {
            if (slot == 53) { player.closeInventory(); return; }
            UUID landId = holder.playersBySlot().get(slot);
            if (landId != null) landManager.land(landId).ifPresent(land -> gui.openMemberMain(player, land));
            return;
        }
        if (holder.screen() == LandManagementHolder.Screen.MAIN) {
            if (slot == 11) new LandMapGui(plugin, landManager).open(player);
            else if (slot == 13) {
                player.closeInventory();
                if (!gui.teleportToOwnedLand(player)) player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "admin.claim-no-teleport"));
            } else if (slot == 15) gui.openSettings(player);
            else if (slot == 17) gui.openLandDeleteConfirmation(player);
            else if (slot == 26) player.closeInventory();
            return;
        }
        if (holder.screen() == LandManagementHolder.Screen.LAND_DELETE_CONFIRM) {
            if (slot == 11) {
                if (plugin.combatTags().blocksClaimAbandon(player)) {
                    player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "combat.claim-abandon-blocked"));
                    gui.openMain(player);
                    return;
                }
                var land = landManager.deleteOwnedLand(player.getUniqueId());
                if (land.isPresent()) {
                    plugin.syncMapIntegrations();
                    plugin.refreshAnalyticsIntegration();
                    player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "gui.management-delete-land-complete", java.util.Map.of("name", land.get().name())));
                }
                gui.openWelcome(player);
            } else if (slot == 15 || slot == 26) gui.openMain(player);
            return;
        }
        if (holder.screen() == LandManagementHolder.Screen.SETTINGS) {
            if (slot == 37) { gui.openMembers(player); return; }
            if (slot == 40) { gui.openMemberList(player); return; }
            if (slot == 43) { renameListener.begin(player); return; }
            if (slot == 53) { gui.openMain(player); return; }
            var land = landManager.ownedBy(player.getUniqueId()).orElseThrow();
            MemberPermissions.Permission memberPermission = switch (slot) {
                case 19 -> MemberPermissions.Permission.BUILD;
                case 20 -> MemberPermissions.Permission.CONTAINERS;
                case 21 -> MemberPermissions.Permission.ENTITIES;
                default -> null;
            };
            if (memberPermission != null) {
                MemberPermissions current = land.memberPermissions();
                boolean enabled = switch (memberPermission) {
                    case BUILD -> !current.build(); case CONTAINERS -> !current.containers(); case ENTITIES -> !current.entities();
                };
                landManager.updateAllMemberPermissions(player.getUniqueId(), memberPermission, enabled);
                plugin.refreshAnalyticsIntegration(); gui.openSettings(player); return;
            }
            LandProtectionSettings.Setting setting = switch (slot) {
                case 10 -> LandProtectionSettings.Setting.MEMBER_PVP;
                case 11 -> LandProtectionSettings.Setting.HOSTILE_MOB_KILLS;
                case 12 -> LandProtectionSettings.Setting.PASSIVE_MOB_KILLS;
                case 13 -> LandProtectionSettings.Setting.HOSTILE_MOB_SPAWNS;
                case 23 -> LandProtectionSettings.Setting.DOORS;
                case 24 -> LandProtectionSettings.Setting.INTERACTIONS;
                default -> null;
            };
            if (setting != null) {
                LandProtectionSettings current = land.protectionSettings();
                boolean enabled = switch (setting) {
                    case MEMBER_PVP -> !current.memberPvp(); case HOSTILE_MOB_KILLS -> !current.hostileMobKills();
                    case PASSIVE_MOB_KILLS -> !current.passiveMobKills(); case HOSTILE_MOB_SPAWNS -> !current.hostileMobSpawns();
                    case DOORS -> !current.doors(); case INTERACTIONS -> !current.interactions();
                };
                landManager.updateProtectionSetting(player.getUniqueId(), setting, enabled);
                plugin.refreshAnalyticsIntegration(); gui.openSettings(player);
            }
            return;
        }
        if (holder.screen() == LandManagementHolder.Screen.ADD_MEMBERS) { if (slot == 53) { gui.openSettings(player); return; } UUID target = holder.playersBySlot().get(slot); if (target != null) { landManager.addMember(player.getUniqueId(), target); plugin.refreshAnalyticsIntegration(); gui.openMemberList(player); } return; }
        if (holder.screen() == LandManagementHolder.Screen.MEMBERS) { if (slot == 53) { gui.openSettings(player); return; } UUID member = holder.playersBySlot().get(slot); if (member != null) gui.openMemberActions(player, member); return; }
        if (holder.screen() == LandManagementHolder.Screen.MEMBER) {
            if (slot == 26) { gui.openMemberList(player); return; }
            if (slot == 22 && holder.memberId() != null) { landManager.removeMember(player.getUniqueId(), holder.memberId()); plugin.refreshAnalyticsIntegration(); gui.openMemberList(player); return; }
            MemberPermissions.Permission individualPermission = switch (slot) {
                case 10 -> MemberPermissions.Permission.BUILD;
                case 13 -> MemberPermissions.Permission.CONTAINERS;
                case 16 -> MemberPermissions.Permission.ENTITIES;
                default -> null;
            };
            if (individualPermission != null && holder.memberId() != null) {
                MemberPermissions current = landManager.members(landManager.ownedBy(player.getUniqueId()).orElseThrow().id()).stream()
                        .filter(member -> member.playerId().equals(holder.memberId())).findFirst().orElseThrow().permissions();
                boolean enabled = switch (individualPermission) {
                    case BUILD -> !current.build();
                    case CONTAINERS -> !current.containers();
                    case ENTITIES -> !current.entities();
                };
                landManager.updateMemberPermission(player.getUniqueId(), holder.memberId(), individualPermission, enabled);
                plugin.refreshAnalyticsIntegration();
                gui.openMemberActions(player, holder.memberId());
            }
            return;
        }
        if (holder.screen() == LandManagementHolder.Screen.DEFAULT_RIGHTS && slot == 26) { gui.openSettings(player); return; }
        MemberPermissions.Permission permission = switch (slot) { case 11 -> MemberPermissions.Permission.BUILD; case 13 -> MemberPermissions.Permission.CONTAINERS; case 15 -> MemberPermissions.Permission.ENTITIES; default -> null; };
        if (permission != null) {
            var current = landManager.ownedBy(player.getUniqueId()).orElseThrow().memberPermissions();
            boolean enabled = switch (permission) {
                case BUILD -> !current.build();
                case CONTAINERS -> !current.containers();
                case ENTITIES -> !current.entities();
            };
            landManager.updateAllMemberPermissions(player.getUniqueId(), permission, enabled);
            plugin.refreshAnalyticsIntegration();
            gui.openDefaultRights(player);
        }
    }
}
