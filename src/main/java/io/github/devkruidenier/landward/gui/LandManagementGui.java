package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.Land;
import io.github.devkruidenier.landward.model.LandMember;
import io.github.devkruidenier.landward.model.MemberPermissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-game member management: select members, toggle rights and remove them. */
public final class LandManagementGui {
    private static final int LARGE_SIZE = 54;
    private static final int COMPACT_SIZE = 27;
    private final LandWardPlugin plugin;
    private final LandManager landManager;

    public LandManagementGui(LandWardPlugin plugin, LandManager landManager) { this.plugin = plugin; this.landManager = landManager; }

    public void openMain(Player player) {
        Optional<Land> optionalLand = landManager.ownedBy(player.getUniqueId());
        if (optionalLand.isEmpty()) { openWelcome(player); return; }
        Land land = optionalLand.get();
        LandManagementHolder holder = holder(LandManagementHolder.Screen.MAIN, null, message(player, "gui.management-title", Map.of("name", land.name())), COMPACT_SIZE);
        Inventory inventory = holder.getInventory();
        inventory.setItem(4, item(Material.WRITABLE_BOOK, land.name(), NamedTextColor.GOLD, List.of(
                message(player, "gui.management-claimed-chunks", Map.of("count", landManager.claimCount(land.id()))),
                message(player, "gui.management-owner", Map.of("name", player.getName()))
        )));
        inventory.setItem(11, item(Material.FILLED_MAP, message(player, "gui.management-chunk-map"), NamedTextColor.AQUA, List.of(message(player, "gui.management-open-map"))));
        inventory.setItem(15, item(Material.COMPARATOR, message(player, "gui.management-settings"), NamedTextColor.YELLOW, List.of(message(player, "gui.management-settings-hint"))));
        inventory.setItem(26, item(Material.ARROW, message(player, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        player.openInventory(inventory);
    }

    public void openSettings(Player player) {
        Optional<Land> optionalLand = landManager.ownedBy(player.getUniqueId());
        if (optionalLand.isEmpty()) { openWelcome(player); return; }
        LandManagementHolder holder = holder(LandManagementHolder.Screen.SETTINGS, null, message(player, "gui.management-title", Map.of("name", optionalLand.get().name())), COMPACT_SIZE);
        Inventory inventory = holder.getInventory();
        inventory.setItem(11, item(Material.LIME_DYE, message(player, "gui.management-add-member"), NamedTextColor.GREEN, List.of(message(player, "gui.management-choose-online"))));
        inventory.setItem(13, item(Material.PLAYER_HEAD, message(player, "gui.management-members"), NamedTextColor.AQUA, List.of(message(player, "gui.management-manage-rights"))));
        inventory.setItem(15, item(Material.NAME_TAG, message(player, "gui.management-rename"), NamedTextColor.YELLOW, List.of(message(player, "gui.management-rename-hint"))));
        inventory.setItem(26, item(Material.ARROW, message(player, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        player.openInventory(inventory);
    }

    public void openWelcome(Player player) {
        LandManagementHolder holder = holder(LandManagementHolder.Screen.WELCOME, null, message(player, "gui.welcome-title"), COMPACT_SIZE);
        Inventory inventory = holder.getInventory();
        inventory.setItem(13, item(Material.GRASS_BLOCK, message(player, "gui.welcome-create"), NamedTextColor.GREEN, List.of(
                message(player, "gui.welcome-create-hint"),
                message(player, "gui.welcome-create-details")
        )));
        inventory.setItem(22, item(Material.FILLED_MAP, message(player, "gui.management-chunk-map"), NamedTextColor.AQUA, List.of(message(player, "gui.management-open-map"))));
        player.openInventory(inventory);
    }

    public void openMembers(Player player) {
        LandManagementHolder holder = holder(LandManagementHolder.Screen.ADD_MEMBERS, null, message(player, "gui.management-add-title"), LARGE_SIZE);
        Inventory inventory = holder.getInventory();
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.getUniqueId().equals(player.getUniqueId()) || landManager.ownedBy(player.getUniqueId()).isEmpty()) continue;
            Land land = landManager.ownedBy(player.getUniqueId()).orElseThrow();
            if (landManager.members(land.id()).stream().anyMatch(member -> member.playerId().equals(target.getUniqueId()))) continue;
            if (slot >= 45) break;
            holder.playersBySlot().put(slot, target.getUniqueId());
            inventory.setItem(slot++, playerHead(target.getUniqueId(), target.getName(), message(player, "gui.management-add-player")));
        }
        inventory.setItem(53, item(Material.ARROW, message(player, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        player.openInventory(inventory);
    }

    public void openMemberList(Player player) {
        Optional<Land> optionalLand = landManager.ownedBy(player.getUniqueId());
        if (optionalLand.isEmpty()) return;
        LandManagementHolder holder = holder(LandManagementHolder.Screen.MEMBERS, null, message(player, "gui.management-members"), LARGE_SIZE);
        Inventory inventory = holder.getInventory();
        int slot = 0;
        for (LandMember member : landManager.members(optionalLand.get().id())) {
            if (slot >= 45) break;
            holder.playersBySlot().put(slot, member.playerId());
            inventory.setItem(slot++, playerHead(member.playerId(), name(member.playerId()), message(player, "gui.management-manage-rights")));
        }
        if (slot == 0) inventory.setItem(22, item(Material.GRAY_DYE, message(player, "gui.management-no-members"), NamedTextColor.GRAY, List.of(message(player, "gui.management-add-member-hint"))));
        inventory.setItem(53, item(Material.ARROW, message(player, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        player.openInventory(inventory);
    }

    public void openRights(Player player, UUID memberId) {
        Optional<Land> optionalLand = landManager.ownedBy(player.getUniqueId());
        if (optionalLand.isEmpty()) return;
        MemberPermissions permissions = landManager.permissions(memberId, optionalLand.get().id());
        LandManagementHolder holder = holder(LandManagementHolder.Screen.RIGHTS, memberId, message(player, "gui.management-rights-title"), LARGE_SIZE);
        Inventory inventory = holder.getInventory();
        inventory.setItem(4, playerHead(memberId, name(memberId), message(player, "gui.management-configure-member")));
        inventory.setItem(20, permissionItem(player, Material.IRON_PICKAXE, message(player, "gui.management-build"), permissions.build()));
        inventory.setItem(22, permissionItem(player, Material.CHEST, message(player, "gui.management-containers"), permissions.containers()));
        inventory.setItem(24, permissionItem(player, Material.CREEPER_SPAWN_EGG, message(player, "gui.management-entities"), permissions.entities()));
        inventory.setItem(49, item(Material.BARRIER, message(player, "gui.management-remove-member"), NamedTextColor.RED, List.of(message(player, "gui.management-remove-hint"))));
        inventory.setItem(53, item(Material.ARROW, message(player, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        player.openInventory(inventory);
    }

    private LandManagementHolder holder(LandManagementHolder.Screen screen, UUID member, String title, int size) {
        LandManagementHolder holder = new LandManagementHolder(screen, member);
        // Inventory headers sit on Minecraft's light GUI background. Dark aqua keeps
        // LandWard's teal identity while retaining strong contrast.
        Inventory inventory = Bukkit.createInventory(holder, size, Component.text(title, NamedTextColor.DARK_AQUA));
        holder.inventory(inventory);
        return holder;
    }
    private ItemStack permissionItem(Player player, Material material, String label, boolean enabled) { return item(material, label + ": " + message(player, enabled ? "gui.management-allowed" : "gui.management-denied"), enabled ? NamedTextColor.GREEN : NamedTextColor.RED, List.of(message(player, "gui.management-toggle"))); }
    private ItemStack playerHead(UUID id, String label, String lore) { ItemStack item = item(Material.PLAYER_HEAD, label, NamedTextColor.AQUA, List.of(lore)); SkullMeta meta = (SkullMeta) item.getItemMeta(); meta.setOwningPlayer(Bukkit.getOfflinePlayer(id)); item.setItemMeta(meta); return item; }
    private ItemStack item(Material material, String label, NamedTextColor color, List<String> lore) { ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta(); meta.displayName(Component.text(label, color).decoration(TextDecoration.ITALIC, false)); meta.lore(lore.stream().map(text -> Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)).toList()); item.setItemMeta(meta); return item; }
    private String name(UUID id) { OfflinePlayer player = Bukkit.getOfflinePlayer(id); return player.getName() == null ? id.toString().substring(0, 8) : player.getName(); }
    private String message(Player player, String key) { return plugin.localeService().message(plugin.localeService().localeFor(player), key); }
    private String message(Player player, String key, Map<String, ?> placeholders) { return plugin.localeService().message(plugin.localeService().localeFor(player), key, placeholders); }
}
