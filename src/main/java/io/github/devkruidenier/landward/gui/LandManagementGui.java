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
import java.util.Optional;
import java.util.UUID;

/** In-game member management: select members, toggle rights and remove them. */
public final class LandManagementGui {
    private static final int SIZE = 54;
    private final LandWardPlugin plugin;
    private final LandManager landManager;

    public LandManagementGui(LandWardPlugin plugin, LandManager landManager) { this.plugin = plugin; this.landManager = landManager; }

    public void openMain(Player player) {
        Optional<Land> optionalLand = landManager.ownedBy(player.getUniqueId());
        if (optionalLand.isEmpty()) { player.sendMessage(message(player, "claim.no-land")); return; }
        Land land = optionalLand.get();
        LandManagementHolder holder = holder(LandManagementHolder.Screen.MAIN, null, player, "LandWard • " + land.name());
        Inventory inventory = holder.getInventory();
        inventory.setItem(4, item(Material.WRITABLE_BOOK, land.name(), NamedTextColor.GOLD, List.of("Claimed chunks: " + landManager.claimCount(land.id()), "Owner: " + player.getName())));
        inventory.setItem(45, item(Material.LIME_DYE, "Add member", NamedTextColor.GREEN, List.of("Choose an online player")));
        inventory.setItem(53, item(Material.FILLED_MAP, "Chunk map", NamedTextColor.AQUA, List.of("Open nearby chunks")));
        int slot = 9;
        for (LandMember member : landManager.members(land.id())) {
            if (slot >= 45) break;
            holder.playersBySlot().put(slot, member.playerId());
            inventory.setItem(slot++, playerHead(member.playerId(), name(member.playerId()), "Click to manage rights"));
        }
        if (slot == 9) inventory.setItem(22, item(Material.GRAY_DYE, "No members yet", NamedTextColor.GRAY, List.of("Use the green dye to add one")));
        player.openInventory(inventory);
    }

    public void openMembers(Player player) {
        LandManagementHolder holder = holder(LandManagementHolder.Screen.MEMBERS, null, player, "LandWard • Add member");
        Inventory inventory = holder.getInventory();
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.getUniqueId().equals(player.getUniqueId()) || landManager.ownedBy(player.getUniqueId()).isEmpty()) continue;
            Land land = landManager.ownedBy(player.getUniqueId()).orElseThrow();
            if (landManager.members(land.id()).stream().anyMatch(member -> member.playerId().equals(target.getUniqueId()))) continue;
            if (slot >= 45) break;
            holder.playersBySlot().put(slot, target.getUniqueId());
            inventory.setItem(slot++, playerHead(target.getUniqueId(), target.getName(), "Click to add this player"));
        }
        inventory.setItem(49, item(Material.ARROW, "Back", NamedTextColor.YELLOW, List.of()));
        player.openInventory(inventory);
    }

    public void openRights(Player player, UUID memberId) {
        Optional<Land> optionalLand = landManager.ownedBy(player.getUniqueId());
        if (optionalLand.isEmpty()) return;
        MemberPermissions permissions = landManager.permissions(memberId, optionalLand.get().id());
        LandManagementHolder holder = holder(LandManagementHolder.Screen.RIGHTS, memberId, player, "LandWard • Member rights");
        Inventory inventory = holder.getInventory();
        inventory.setItem(4, playerHead(memberId, name(memberId), "Configure this member"));
        inventory.setItem(20, permissionItem(Material.IRON_PICKAXE, "Build", permissions.build()));
        inventory.setItem(22, permissionItem(Material.CHEST, "Containers", permissions.containers()));
        inventory.setItem(24, permissionItem(Material.CREEPER_SPAWN_EGG, "Entities", permissions.entities()));
        inventory.setItem(49, item(Material.ARROW, "Back", NamedTextColor.YELLOW, List.of()));
        inventory.setItem(53, item(Material.BARRIER, "Remove member", NamedTextColor.RED, List.of("This removes all their land rights")));
        player.openInventory(inventory);
    }

    private LandManagementHolder holder(LandManagementHolder.Screen screen, UUID member, Player player, String title) {
        LandManagementHolder holder = new LandManagementHolder(screen, member);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text(title, NamedTextColor.GOLD));
        holder.inventory(inventory);
        return holder;
    }
    private ItemStack permissionItem(Material material, String label, boolean enabled) { return item(material, label + ": " + (enabled ? "Allowed" : "Denied"), enabled ? NamedTextColor.GREEN : NamedTextColor.RED, List.of("Click to toggle")); }
    private ItemStack playerHead(UUID id, String label, String lore) { ItemStack item = item(Material.PLAYER_HEAD, label, NamedTextColor.AQUA, List.of(lore)); SkullMeta meta = (SkullMeta) item.getItemMeta(); meta.setOwningPlayer(Bukkit.getOfflinePlayer(id)); item.setItemMeta(meta); return item; }
    private ItemStack item(Material material, String label, NamedTextColor color, List<String> lore) { ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta(); meta.displayName(Component.text(label, color).decoration(TextDecoration.ITALIC, false)); meta.lore(lore.stream().map(text -> Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)).toList()); item.setItemMeta(meta); return item; }
    private String name(UUID id) { OfflinePlayer player = Bukkit.getOfflinePlayer(id); return player.getName() == null ? id.toString().substring(0, 8) : player.getName(); }
    private String message(Player player, String key) { return plugin.localeService().message(plugin.localeService().localeFor(player), key); }
}
