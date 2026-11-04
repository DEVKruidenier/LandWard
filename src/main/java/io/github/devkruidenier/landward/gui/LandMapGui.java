package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.model.Land;
import io.github.devkruidenier.landward.model.MemberPermissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Optional;

public final class LandMapGui {

    private static final int SIZE = 54;
    private static final int MAP_START_COLUMN = 2;
    private static final int MAP_SIZE = 5;

    private final LandWardPlugin plugin;
    private final LandManager landManager;

    public LandMapGui(LandWardPlugin plugin, LandManager landManager) {
        this.plugin = plugin;
        this.landManager = landManager;
    }

    public void open(Player player) {
        String locale = plugin.localeService().localeFor(player);
        LandMapHolder holder = new LandMapHolder();
        Inventory inventory = Bukkit.createInventory(
                holder,
                SIZE,
                Component.text(plugin.localeService().message(locale, "gui.map-title"), NamedTextColor.GOLD)
        );
        holder.setInventory(inventory);

        fillFrame(inventory);
        fillMap(inventory, player, locale);
        fillLegend(inventory, locale);
        player.openInventory(inventory);
    }

    private void fillFrame(Inventory inventory) {
        ItemStack frame = item(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.BLACK, List.of());
        for (int row = 0; row < 5; row++) {
            inventory.setItem(row * 9, frame);
            inventory.setItem(row * 9 + 1, frame);
            inventory.setItem(row * 9 + 7, frame);
            inventory.setItem(row * 9 + 8, frame);
        }
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, frame);
        }
    }

    private void fillMap(Inventory inventory, Player player, String locale) {
        int centerX = player.getLocation().getBlockX() >> 4;
        int centerZ = player.getLocation().getBlockZ() >> 4;
        for (int mapZ = 0; mapZ < MAP_SIZE; mapZ++) {
            for (int mapX = 0; mapX < MAP_SIZE; mapX++) {
                int chunkX = centerX + mapX - 2;
                int chunkZ = centerZ + mapZ - 2;
                boolean current = mapX == 2 && mapZ == 2;
                ChunkPosition position = new ChunkPosition(player.getWorld().getUID(), chunkX, chunkZ);
                Optional<Land> land = landManager.landAt(position);
                inventory.setItem(
                        mapZ * 9 + MAP_START_COLUMN + mapX,
                        chunkItem(locale, player, land, chunkX, chunkZ, current)
                );
            }
        }
    }

    private ItemStack chunkItem(
            String locale,
            Player player,
            Optional<Land> land,
            int chunkX,
            int chunkZ,
            boolean current
    ) {
        Material material;
        NamedTextColor color;
        String title;
        String status;
        if (land.isEmpty()) {
            material = Material.GRAY_STAINED_GLASS_PANE;
            color = NamedTextColor.GRAY;
            title = plugin.localeService().message(locale, "gui.unclaimed");
            status = plugin.localeService().message(locale, "gui.status-unclaimed");
        } else if (land.get().ownerId().equals(player.getUniqueId())
                || !landManager.permissions(player.getUniqueId(), land.get().id()).equals(MemberPermissions.NONE)) {
            material = Material.LIME_STAINED_GLASS_PANE;
            color = NamedTextColor.GREEN;
            title = land.get().name();
            status = plugin.localeService().message(locale, land.get().ownerId().equals(player.getUniqueId())
                    ? "gui.status-yours"
                    : "gui.status-member");
        } else {
            material = Material.RED_STAINED_GLASS_PANE;
            color = NamedTextColor.RED;
            title = land.get().name();
            status = plugin.localeService().message(locale, "gui.status-other");
        }
        if (current) {
            title = plugin.localeService().message(locale, "gui.current-chunk") + " • " + title;
        }
        return item(material, title, color, List.of(
                Component.text(status, NamedTextColor.DARK_GRAY),
                Component.text("X: " + chunkX + "  Z: " + chunkZ, NamedTextColor.GRAY)
        ));
    }

    private void fillLegend(Inventory inventory, String locale) {
        inventory.setItem(46, item(
                Material.LIME_STAINED_GLASS_PANE,
                plugin.localeService().message(locale, "gui.legend-yours"),
                NamedTextColor.GREEN,
                List.of()
        ));
        inventory.setItem(49, item(
                Material.GRAY_STAINED_GLASS_PANE,
                plugin.localeService().message(locale, "gui.legend-unclaimed"),
                NamedTextColor.GRAY,
                List.of()
        ));
        inventory.setItem(52, item(
                Material.RED_STAINED_GLASS_PANE,
                plugin.localeService().message(locale, "gui.legend-other"),
                NamedTextColor.RED,
                List.of()
        ));
    }

    private ItemStack item(Material material, String title, NamedTextColor color, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(title, color).decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) {
            meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        }
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }
}
