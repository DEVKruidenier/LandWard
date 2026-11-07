package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (!player.hasPermission("landward.admin")) { player.closeInventory(); return; }
        ClaimAdminGui gui = new ClaimAdminGui(plugin, lands);
        if (holder.screen() == ClaimAdminHolder.Screen.MAIN) {
            if (event.getRawSlot() == 9) gui.openPlayerClaims(player);
            else if (event.getRawSlot() == 11) new LandMapGui(plugin, lands).openAdminClaims(player);
            else if (event.getRawSlot() == 13) gui.openAdminSettings(player);
            else if (event.getRawSlot() == 15) {
                plugin.reloadLandWard();
                player.sendMessage(Component.text("LandWard » ", NamedTextColor.GOLD)
                        .append(Component.text(plugin.localeService().message(plugin.localeService().localeFor(player), "general.reloaded"), NamedTextColor.GREEN)));
                gui.openMain(player);
            }
            else if (event.getRawSlot() == 17) gui.openWorlds(player);
            else if (event.getRawSlot() == 26) player.closeInventory();
            return;
        }
        if (holder.screen() == ClaimAdminHolder.Screen.PLAYER_CLAIMS) {
            if (event.getRawSlot() == 53) { gui.openMain(player); return; }
            UUID landId = holder.landsBySlot().get(event.getRawSlot());
            if (landId != null) gui.openPlayerClaimDetails(player, landId);
            return;
        }
        if (holder.screen() == ClaimAdminHolder.Screen.PLAYER_CLAIM_DETAILS) {
            UUID landId = holder.selectedLandId();
            if (landId == null) { gui.openPlayerClaims(player); return; }
            if (event.getRawSlot() == 26) { gui.openPlayerClaims(player); return; }
            if (event.getRawSlot() == 10) {
                if (!gui.teleportToClaim(player, landId)) player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "admin.claim-no-teleport"));
                return;
            }
            if (event.getRawSlot() == 12) { player.closeInventory(); plugin.adminOwnerChangeListener().begin(player, landId); return; }
            if (event.getRawSlot() == 16) gui.openPlayerClaimMembers(player, landId);
            return;
        }
        if (holder.screen() == ClaimAdminHolder.Screen.PLAYER_CLAIM_MEMBERS) {
            if (event.getRawSlot() == 53 && holder.selectedLandId() != null) gui.openPlayerClaimDetails(player, holder.selectedLandId());
            return;
        }
        if (holder.screen() == ClaimAdminHolder.Screen.ADMIN_SETTINGS) {
            if (event.getRawSlot() == 53) { gui.openMain(player); return; }
            if (event.getRawSlot() == 37) { gui.openAdminMemberChoices(player); return; }
            if (event.getRawSlot() == 40) { gui.openAdminMemberList(player); return; }
            String key = switch (event.getRawSlot()) {
                case 10 -> "protection.allow-pvp";
                case 11 -> "protection.allow-hostile-mob-kills";
                case 12 -> "protection.allow-passive-mob-kills";
                case 13 -> "protection.allow-hostile-mob-spawns";
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
            if (event.getRawSlot() == 53) { gui.openAdminSettings(player); return; }
            UUID member = holder.playersBySlot().get(event.getRawSlot());
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
            if (event.getRawSlot() == 53) { gui.openAdminSettings(player); return; }
            UUID member = holder.playersBySlot().get(event.getRawSlot());
            if (member != null) gui.openAdminMemberActions(player, member);
            return;
        }
        if (holder.screen() == ClaimAdminHolder.Screen.ADMIN_MEMBER) {
            if (event.getRawSlot() == 26) { gui.openAdminMemberList(player); return; }
            UUID member = holder.selectedMemberId();
            if (member == null) { gui.openAdminMemberList(player); return; }
            if (event.getRawSlot() == 22) {
                try {
                    lands.removeAdministrativeMember(member);
                    plugin.refreshAnalyticsIntegration();
                } catch (io.github.devkruidenier.landward.storage.StorageException exception) {
                    player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "general.storage-error"));
                }
                gui.openAdminMemberList(player);
                return;
            }
            io.github.devkruidenier.landward.model.MemberPermissions.Permission permission = switch (event.getRawSlot()) {
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
        if (event.getRawSlot() == 53) { gui.openMain(player); return; }
        String worldName = holder.worldsBySlot().get(event.getRawSlot());
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
