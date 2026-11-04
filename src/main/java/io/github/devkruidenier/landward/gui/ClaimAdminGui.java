package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Administrator-only entry point for claim moderation and world activation. */
public final class ClaimAdminGui {
    private final LandWardPlugin plugin;
    private final LandManager lands;

    public ClaimAdminGui(LandWardPlugin plugin, LandManager lands) { this.plugin = plugin; this.lands = lands; }

    public void openMain(Player player) {
        String locale = plugin.localeService().localeFor(player);
        ClaimAdminHolder holder = holder(ClaimAdminHolder.Screen.MAIN, locale, 27, "gui.admin-title");
        Inventory inventory = holder.getInventory();
        inventory.setItem(9, item(Material.FILLED_MAP, plugin.localeService().message(locale, "gui.admin-player-claims-title"), NamedTextColor.AQUA, List.of(plugin.localeService().message(locale, "gui.admin-player-claims-hint"))));
        inventory.setItem(11, item(Material.OBSIDIAN, plugin.localeService().message(locale, "gui.admin-claims-title"), NamedTextColor.LIGHT_PURPLE, List.of(plugin.localeService().message(locale, "gui.admin-claims-hint"))));
        inventory.setItem(13, item(Material.COMPARATOR, plugin.localeService().message(locale, "gui.admin-settings"), NamedTextColor.LIGHT_PURPLE, List.of(plugin.localeService().message(locale, "gui.admin-settings-hint"))));
        inventory.setItem(15, item(Material.CLOCK, plugin.localeService().message(locale, "gui.admin-reload"), NamedTextColor.YELLOW, List.of(plugin.localeService().message(locale, "gui.admin-reload-hint"))));
        inventory.setItem(17, item(Material.GRASS_BLOCK, plugin.localeService().message(locale, "gui.admin-worlds"), NamedTextColor.GREEN, List.of(plugin.localeService().message(locale, "gui.admin-worlds-hint"))));
        inventory.setItem(26, item(Material.ARROW, plugin.localeService().message(locale, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        player.openInventory(inventory);
    }

    public void openWorlds(Player player) {
        String locale = plugin.localeService().localeFor(player);
        ClaimAdminHolder holder = holder(ClaimAdminHolder.Screen.WORLDS, locale, 54, "gui.admin-worlds-title");
        Inventory inventory = holder.getInventory();
        int slot = 0;
        for (World world : Bukkit.getWorlds()) {
            if (slot >= 45) break;
            boolean active = plugin.isLandWorld(world);
            holder.worldsBySlot().put(slot, world.getName());
            inventory.setItem(slot++, item(active ? Material.LIME_DYE : Material.GRAY_DYE,
                    plugin.localeService().message(locale, active ? "gui.admin-world-enabled" : "gui.admin-world-disabled"),
                    active ? NamedTextColor.GREEN : NamedTextColor.RED, List.of(
                    world.getName(),
                    plugin.localeService().message(locale, active ? "gui.admin-world-disable-hint" : "gui.admin-world-enable-hint")
            )));
        }
        inventory.setItem(53, item(Material.ARROW, plugin.localeService().message(locale, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        player.openInventory(inventory);
    }

    public void openAdminSettings(Player player) {
        String locale = plugin.localeService().localeFor(player);
        ClaimAdminHolder holder = holder(ClaimAdminHolder.Screen.ADMIN_SETTINGS, locale, 27, "gui.admin-settings");
        Inventory inventory = holder.getInventory();
        setting(inventory, 10, Material.IRON_SWORD, locale, "gui.admin-pvp", "protection.allow-pvp");
        setting(inventory, 11, Material.ZOMBIE_HEAD, locale, "gui.admin-hostile-mob-kills", "protection.allow-hostile-mob-kills");
        setting(inventory, 12, Material.BEEF, locale, "gui.admin-passive-mob-kills", "protection.allow-passive-mob-kills");
        setting(inventory, 14, Material.CHEST, locale, "gui.management-containers", "protection.allow-containers");
        setting(inventory, 15, Material.OAK_DOOR, locale, "gui.admin-doors", "protection.allow-doors");
        setting(inventory, 16, Material.OAK_SIGN, locale, "gui.admin-interactions", "protection.allow-interactions");
        setting(inventory, 17, Material.CREEPER_SPAWN_EGG, locale, "gui.admin-hostile-mob-spawns", "protection.allow-hostile-mob-spawns");
        inventory.setItem(19, item(Material.LIME_DYE, plugin.localeService().message(locale, "gui.management-add-member"), NamedTextColor.GREEN, List.of(plugin.localeService().message(locale, "gui.management-choose-online"))));
        inventory.setItem(21, item(Material.PLAYER_HEAD, plugin.localeService().message(locale, "gui.management-members"), NamedTextColor.AQUA, List.of(plugin.localeService().message(locale, "gui.admin-members-hint"))));
        inventory.setItem(26, item(Material.ARROW, plugin.localeService().message(locale, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        player.openInventory(inventory);
    }

    public void openAdminMemberChoices(Player player) {
        String locale = plugin.localeService().localeFor(player);
        ClaimAdminHolder holder = holder(ClaimAdminHolder.Screen.ADMIN_ADD_MEMBERS, locale, 54, "gui.management-add-title");
        Inventory inventory = holder.getInventory();
        Optional<io.github.devkruidenier.landward.model.Land> land = lands.administrativeLand();
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (slot >= 45 || land.isEmpty() || lands.permissions(target.getUniqueId(), land.get().id()).equals(io.github.devkruidenier.landward.model.MemberPermissions.FULL)) continue;
            holder.playersBySlot().put(slot, target.getUniqueId());
            inventory.setItem(slot++, playerHead(target.getUniqueId(), target.getName(), plugin.localeService().message(locale, "gui.management-add-player")));
        }
        inventory.setItem(53, item(Material.ARROW, plugin.localeService().message(locale, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        player.openInventory(inventory);
    }

    public void openAdminMemberList(Player player) {
        String locale = plugin.localeService().localeFor(player);
        ClaimAdminHolder holder = holder(ClaimAdminHolder.Screen.ADMIN_MEMBERS, locale, 54, "gui.management-members");
        Inventory inventory = holder.getInventory();
        int slot = 0;
        for (var member : lands.administrativeLand().map(value -> lands.members(value.id())).orElse(List.of())) {
            if (slot >= 45) break;
            holder.playersBySlot().put(slot, member.playerId());
            inventory.setItem(slot++, playerHead(member.playerId(), name(member.playerId()), plugin.localeService().message(locale, "gui.admin-member-remove-hint")));
        }
        if (slot == 0) inventory.setItem(22, item(Material.GRAY_DYE, plugin.localeService().message(locale, "gui.management-no-members"), NamedTextColor.GRAY, List.of()));
        inventory.setItem(53, item(Material.ARROW, plugin.localeService().message(locale, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        player.openInventory(inventory);
    }

    private ClaimAdminHolder holder(ClaimAdminHolder.Screen screen, String locale, int size, String titleKey) {
        ClaimAdminHolder holder = new ClaimAdminHolder(screen);
        Inventory inventory = Bukkit.createInventory(holder, size, Component.text(plugin.localeService().message(locale, titleKey), NamedTextColor.DARK_AQUA));
        holder.inventory(inventory);
        return holder;
    }

    private ItemStack item(Material material, String label, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private void setting(Inventory inventory, int slot, Material material, String locale, String titleKey, String configKey) {
        boolean enabled = plugin.getConfig().getBoolean("admin-claims." + configKey, false);
        inventory.setItem(slot, item(material,
                plugin.localeService().message(locale, titleKey) + ": " + plugin.localeService().message(locale, enabled ? "gui.management-allowed" : "gui.management-denied"),
                enabled ? NamedTextColor.GREEN : NamedTextColor.RED,
                List.of(plugin.localeService().message(locale, "gui.management-toggle"))));
    }

    private ItemStack playerHead(UUID id, String label, String lore) {
        ItemStack item = item(Material.PLAYER_HEAD, label, NamedTextColor.AQUA, List.of(lore));
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(id));
        item.setItemMeta(meta);
        return item;
    }

    private String name(UUID id) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(id);
        return player.getName() == null ? id.toString().substring(0, 8) : player.getName();
    }
}
