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
import java.util.Map;
import java.util.Optional;

public final class LandMapGui {

    private static final int SIZE = 54;
    private static final int MAP_WIDTH = 9;
    private static final int MAP_HEIGHT = 6;

    private final LandWardPlugin plugin;
    private final LandManager landManager;

    public LandMapGui(LandWardPlugin plugin, LandManager landManager) {
        this.plugin = plugin;
        this.landManager = landManager;
    }

    public void open(Player player) {
        open(player, false);
    }

    public void openAdmin(Player player) {
        open(player, true);
    }

    private void open(Player player, boolean administrator) {
        String locale = plugin.localeService().localeFor(player);
        LandMapHolder holder = new LandMapHolder(administrator);
        Inventory inventory = Bukkit.createInventory(
                holder,
                SIZE,
                Component.text(plugin.localeService().message(locale, administrator ? "gui.admin-map-title" : "gui.map-title"), NamedTextColor.GOLD)
        );
        holder.setInventory(inventory);

        fillMap(inventory, holder, player, locale);
        inventory.setItem(53, item(
                Material.ARROW,
                plugin.localeService().message(locale, "gui.management-back"),
                NamedTextColor.YELLOW,
                List.of()
        ));
        player.openInventory(inventory);
    }

    public void openUnclaimConfirmation(Player player, ChunkPosition position, boolean administrator) {
        String locale = plugin.localeService().localeFor(player);
        UnclaimConfirmationHolder holder = new UnclaimConfirmationHolder(position, administrator);
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text(plugin.localeService().message(locale, administrator ? "gui.admin-confirm-title" : "gui.unclaim-confirm-title"), NamedTextColor.GOLD));
        holder.setInventory(inventory);
        ItemStack frame = item(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, frame);
        inventory.setItem(11, item(Material.LIME_DYE, plugin.localeService().message(locale, administrator ? "gui.admin-confirm" : "gui.unclaim-confirm"), NamedTextColor.GREEN, List.of(
                Component.text("X: " + position.x() + "  Z: " + position.z(), NamedTextColor.GRAY),
                Component.text(plugin.localeService().message(locale, "gui.unclaim-warning"), NamedTextColor.RED)
        )));
        inventory.setItem(15, item(Material.BARRIER, plugin.localeService().message(locale, "gui.cancel"), NamedTextColor.RED, List.of()));
        inventory.setItem(22, item(Material.ARROW, plugin.localeService().message(locale, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        player.openInventory(inventory);
    }

    private void fillMap(Inventory inventory, LandMapHolder holder, Player player, String locale) {
        int centerX = player.getLocation().getBlockX() >> 4;
        int centerZ = player.getLocation().getBlockZ() >> 4;
        for (int mapZ = 0; mapZ < MAP_HEIGHT; mapZ++) {
            for (int mapX = 0; mapX < MAP_WIDTH; mapX++) {
                int chunkX = centerX + mapX - 4;
                int chunkZ = centerZ + mapZ - 2;
                boolean current = mapX == 4 && mapZ == 2; // Slot 22: always the player's current chunk.
                ChunkPosition position = new ChunkPosition(player.getWorld().getUID(), chunkX, chunkZ);
                Optional<Land> land = landManager.landAt(position);
                int slot = mapZ * 9 + mapX;
                if (slot == 53) continue; // Reserved for the only navigation control.
                holder.chunksBySlot().put(slot, position);
                inventory.setItem(
                        slot,
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
        java.util.ArrayList<Component> lore = new java.util.ArrayList<>();
        if (current) lore.add(Component.text(plugin.localeService().message(locale, "gui.current-chunk"), NamedTextColor.GOLD));
        lore.add(Component.text(status, NamedTextColor.DARK_GRAY));
        if (land.isEmpty()) {
            lore.add(Component.text(plugin.localeService().message(locale, "gui.click-to-claim"), NamedTextColor.GREEN));
        } else {
            String owner = Bukkit.getOfflinePlayer(land.get().ownerId()).getName();
            lore.add(Component.text(
                    plugin.localeService().message(locale, "gui.management-owner", Map.of("name", owner == null ? "-" : owner)),
                    NamedTextColor.AQUA
            ));
        }
        lore.add(Component.text("X: " + chunkX + "  Z: " + chunkZ, NamedTextColor.GRAY));
        if (current) {
            material = Material.COMPASS;
            color = NamedTextColor.GOLD;
            title = plugin.localeService().message(locale, "gui.current-chunk");
        }
        return item(material, title, color, List.of(
                lore.toArray(Component[]::new)
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
