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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Administrator-only entry point for claim moderation and world activation. */
public final class ClaimAdminGui {
    private final LandWardPlugin plugin;
    private final LandManager lands;

    public ClaimAdminGui(LandWardPlugin plugin, LandManager lands) { this.plugin = plugin; this.lands = lands; }

    public void openMain(Player player) {
        String locale = plugin.localeService().localeFor(player);
        ClaimAdminHolder holder = holder(ClaimAdminHolder.Screen.MAIN, locale, 27, "gui.admin-title");
        Inventory inventory = holder.getInventory();
        inventory.setItem(11, item(Material.FILLED_MAP, plugin.localeService().message(locale, "gui.admin-map-title"), NamedTextColor.AQUA, List.of(plugin.localeService().message(locale, "gui.admin-map-hint"))));
        inventory.setItem(13, item(Material.CLOCK, plugin.localeService().message(locale, "gui.admin-reload"), NamedTextColor.YELLOW, List.of(plugin.localeService().message(locale, "gui.admin-reload-hint"))));
        inventory.setItem(15, item(Material.GRASS_BLOCK, plugin.localeService().message(locale, "gui.admin-worlds"), NamedTextColor.GREEN, List.of(plugin.localeService().message(locale, "gui.admin-worlds-hint"))));
        inventory.setItem(22, item(Material.ARROW, plugin.localeService().message(locale, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
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
        inventory.setItem(49, item(Material.ARROW, plugin.localeService().message(locale, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
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
}
