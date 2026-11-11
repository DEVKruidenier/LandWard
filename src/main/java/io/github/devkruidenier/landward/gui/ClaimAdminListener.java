package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.UUID;

/** Handles the administrative claim menu without exposing mutating actions to players. */
public final class ClaimAdminListener implements Listener {
    private final LandWardPlugin plugin;
    private final LandManager lands;

    public ClaimAdminListener(LandWardPlugin plugin, LandManager lands) { this.plugin = plugin; this.lands = lands; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ClaimAdminHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (!player.hasPermission("landward.admin")) { player.closeInventory(); return; }
        if (GuiLayout.navigation(event)) return;
        int slot = GuiLayout.action(event.getView().getTopInventory(), event.getRawSlot());
        if (slot < 0) return;
        ClaimAdminGui gui = new ClaimAdminGui(plugin, lands);
        if (holder.screen() == ClaimAdminHolder.Screen.MAIN) {
            if (slot == 9) gui.openPlayerClaims(player);
            else if (slot == 11) new LandMapGui(plugin, lands).openAdminClaims(player);
            else if (slot == 13) gui.openAdminSettings(player);
            else if (slot == 15) {
                plugin.reloadLandWard();
                player.sendMessage(ChatColor.GOLD + "LandWard " + ChatColor.DARK_GRAY + "» " + ChatColor.GREEN
                        + plugin.localeService().message(plugin.localeService().localeFor(player), "general.reloaded"));
                gui.openMain(player);
            }
            else if (slot == 17) gui.openWorlds(player);
            else if (slot == 20) gui.openAdminLandDeleteConfirmation(player);
            else if (slot == 26) player.closeInventory();
            return;
        }
        if (holder.screen() == ClaimAdminHolder.Screen.PLAYER_CLAIMS) {
            if (slot == 53) { gui.openMain(player); return; }
            UUID landId = holder.landsBySlot().get(slot);
            if (landId != null) gui.openPlayerClaimRemoval(player, landId);
            return;
        }
        if (holder.screen() == ClaimAdminHolder.Screen.PLAYER_CLAIM_DETAILS) {
            UUID landId = holder.selectedLandId();
            if (landId == null) { gui.openPlayerClaims(player); return; }
            if (slot == 26) { gui.openPlayerClaims(player); return; }
            if (slot == 10) {
                if (!gui.teleportToClaim(player, landId)) player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "admin.claim-no-teleport"));
                return;
            }
            if (slot == 12) { player.closeInventory(); plugin.adminOwnerChangeListener().begin(player, landId); return; }
            if (slot == 16) gui.openPlayerClaimMembers(player, landId);
            if (slot == 18) {
                if (gui.teleportToClaim(player, landId)) new LandMapGui(plugin, lands).openAdmin(player, landId);
                else player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "admin.claim-no-teleport"));
                return;
            }
            if (slot == 20) gui.openPlayerClaimRemoval(player, landId);
            return;
        }
        if (holder.screen() == ClaimAdminHolder.Screen.PLAYER_CLAIM_MEMBERS) {
            if (slot == 53 && holder.selectedLandId() != null) gui.openPlayerClaimDetails(player, holder.selectedLandId());
            return;
        }
        if (holder.screen() == ClaimAdminHolder.Screen.PLAYER_CLAIM_REMOVE) {
            UUID landId = holder.selectedLandId();
            if (landId == null) { gui.openPlayerClaims(player); return; }
            if (slot == 11) {
                if (gui.teleportToClaim(player, landId)) new LandMapGui(plugin, lands).openAdmin(player, landId);
                else player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "admin.claim-no-teleport"));
                return;
            }
            if (slot == 15) gui.openPlayerLandDeleteConfirmation(player, landId);
            else if (slot == 26) gui.openPlayerClaimDetails(player, landId);
            return;
        }
        if (holder.screen() == ClaimAdminHolder.Screen.PLAYER_LAND_DELETE_CONFIRM) {
            UUID landId = holder.selectedLandId();
            if (slot == 11 && landId != null) {
                try {
                    var land = lands.land(landId).filter(value -> !lands.isAdministrative(value)).orElse(null);
                    if (land != null) {
                        lands.deleteOwnedLand(land.ownerId());
                        plugin.syncMapIntegrations();
                        plugin.refreshAnalyticsIntegration();
                    }
                } catch (io.github.devkruidenier.landward.storage.StorageException exception) {
                    player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "general.storage-error"));
                }
            }
            if (slot == 11 || slot == 15 || slot == 26) gui.openPlayerClaims(player);
            return;
        }
        if (holder.screen() == ClaimAdminHolder.Screen.ADMIN_LAND_DELETE_CONFIRM) {
            if (slot == 11) {
                try {
                    lands.deleteAdministrativeLand();
                    plugin.syncMapIntegrations();
                    plugin.refreshAnalyticsIntegration();
                } catch (io.github.devkruidenier.landward.storage.StorageException exception) {
                    player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "general.storage-error"));
                }
            }
            if (slot == 11 || slot == 15 || slot == 26) gui.openMain(player);
            return;
        }
        if (holder.screen() == ClaimAdminHolder.Screen.ADMIN_SETTINGS) {
            if (slot == 10 && !player.getWorld().getPVP()) { gui.openAdminSettings(player); return; }
            if (slot == 53) { gui.openMain(player); return; }
            if (slot == 37) { gui.openAdminMemberChoices(player); return; }
            if (slot == 40) { gui.openAdminMemberList(player); return; }
            String key = switch (slot) {
                case 10 -> "protection.allow-pvp";
                case 11 -> "protection.allow-hostile-mob-kills";
                case 12 -> "protection.allow-passive-mob-kills";
                case 13 -> "protection.allow-hostile-mob-spawns";
                case 14 -> "protection.allow-build";
                case 15 -> "protection.allow-entities";
                case 19 -> "protection.allow-containers";
                case 20 -> "protection.allow-doors";
                case 21 -> "protection.allow-interactions";
                default -> null;
            };
            if (key != null) {
                String path = "admin-claims." + key;
                plugin.getConfig().set(path, !plugin.getConfig().getBoolean(path, false));
                plugin.saveConfig();
                gui.openAdminSettings(player);
            }
            return;
        }
        if (holder.screen() == ClaimAdminHolder.Screen.ADMIN_ADD_MEMBERS) {
            if (slot == 53) { gui.openAdminSettings(player); return; }
            UUID member = holder.playersBySlot().get(slot);
            if (member != null) {
                try {
                    lands.addAdministrativeMember(member);
                    plugin.refreshAnalyticsIntegration();
                } catch (io.github.devkruidenier.landward.storage.StorageException exception) {
                    player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "general.storage-error"));
                }
                gui.openAdminMemberList(player);
            }
            return;
        }
        if (holder.screen() == ClaimAdminHolder.Screen.ADMIN_MEMBERS) {
            if (slot == 53) { gui.openAdminSettings(player); return; }
            UUID member = holder.playersBySlot().get(slot);
            if (member != null) gui.openAdminMemberActions(player, member);
            return;
        }
        if (holder.screen() == ClaimAdminHolder.Screen.ADMIN_MEMBER) {
            if (slot == 26) { gui.openAdminMemberList(player); return; }
            UUID member = holder.selectedMemberId();
            if (member == null) { gui.openAdminMemberList(player); return; }
            if (slot == 22) {
                try {
                    lands.removeAdministrativeMember(member);
                    plugin.refreshAnalyticsIntegration();
                } catch (io.github.devkruidenier.landward.storage.StorageException exception) {
                    player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "general.storage-error"));
                }
                gui.openAdminMemberList(player);
                return;
            }
            io.github.devkruidenier.landward.model.MemberPermissions.Permission permission = switch (slot) {
                case 10 -> io.github.devkruidenier.landward.model.MemberPermissions.Permission.BUILD;
                case 13 -> io.github.devkruidenier.landward.model.MemberPermissions.Permission.CONTAINERS;
                case 16 -> io.github.devkruidenier.landward.model.MemberPermissions.Permission.ENTITIES;
                default -> null;
            };
            if (permission != null) {
                var land = lands.administrativeLand().orElse(null);
                var current = land == null ? null : lands.members(land.id()).stream()
                        .filter(value -> value.playerId().equals(member)).findFirst().orElse(null);
                if (current != null) {
                    boolean enabled = switch (permission) {
                        case BUILD -> !current.permissions().build();
                        case CONTAINERS -> !current.permissions().containers();
                        case ENTITIES -> !current.permissions().entities();
                    };
                    try {
                        lands.updateAdministrativeMemberPermission(member, permission, enabled);
                        plugin.refreshAnalyticsIntegration();
                    } catch (io.github.devkruidenier.landward.storage.StorageException exception) {
                        player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "general.storage-error"));
                    }
                }
                gui.openAdminMemberActions(player, member);
            }
            return;
        }
        if (slot == 53) { gui.openMain(player); return; }
        String worldName = holder.worldsBySlot().get(slot);
        if (worldName == null) return;
        World world = Bukkit.getWorld(worldName);
        if (world == null) { gui.openWorlds(player); return; }
        plugin.setLandWorldEnabled(world, !plugin.isLandWorld(world));
        gui.openWorlds(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ClaimAdminHolder) event.setCancelled(true);
    }
}
