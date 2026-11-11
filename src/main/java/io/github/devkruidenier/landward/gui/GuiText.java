package io.github.devkruidenier.landward.gui;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Bukkit-string GUI helpers. Paper also supports these legacy API methods,
 * while Spigot does not expose Paper's Adventure Component inventory methods.
 */
final class GuiText {
    private GuiText() {
    }

    static Inventory inventory(InventoryHolder holder, int size, String title) {
        Inventory inventory = Bukkit.createInventory(holder, size, ChatColor.DARK_AQUA + title);
        GuiLayout.title(inventory, ChatColor.DARK_AQUA + title);
        return inventory;
    }

    static ItemStack item(Material material, String label, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(color) + label);
        if (!lore.isEmpty()) {
            meta.setLore(lore.stream().map(line -> ChatColor.GRAY + line).toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    static String color(NamedTextColor color) {
        if (color.equals(NamedTextColor.BLACK)) return ChatColor.BLACK.toString();
        if (color.equals(NamedTextColor.DARK_BLUE)) return ChatColor.DARK_BLUE.toString();
        if (color.equals(NamedTextColor.DARK_GREEN)) return ChatColor.DARK_GREEN.toString();
        if (color.equals(NamedTextColor.DARK_AQUA)) return ChatColor.DARK_AQUA.toString();
        if (color.equals(NamedTextColor.DARK_RED)) return ChatColor.DARK_RED.toString();
        if (color.equals(NamedTextColor.DARK_PURPLE)) return ChatColor.DARK_PURPLE.toString();
        if (color.equals(NamedTextColor.GOLD)) return ChatColor.GOLD.toString();
        if (color.equals(NamedTextColor.GRAY)) return ChatColor.GRAY.toString();
        if (color.equals(NamedTextColor.DARK_GRAY)) return ChatColor.DARK_GRAY.toString();
        if (color.equals(NamedTextColor.BLUE)) return ChatColor.BLUE.toString();
        if (color.equals(NamedTextColor.GREEN)) return ChatColor.GREEN.toString();
        if (color.equals(NamedTextColor.AQUA)) return ChatColor.AQUA.toString();
        if (color.equals(NamedTextColor.RED)) return ChatColor.RED.toString();
        if (color.equals(NamedTextColor.LIGHT_PURPLE)) return ChatColor.LIGHT_PURPLE.toString();
        if (color.equals(NamedTextColor.YELLOW)) return ChatColor.YELLOW.toString();
        return ChatColor.WHITE.toString();
    }
}
