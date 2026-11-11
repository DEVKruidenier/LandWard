package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.LandWardPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.*;
import java.util.function.IntConsumer;

/** Presentation mapping keeps domain actions independent of displayed slot positions. */
final class GuiLayout {
    private static final Map<Inventory, Map<Integer, Integer>> ACTIONS = new WeakHashMap<>();
    private static final Map<Inventory, Runnable[]> PAGES = new WeakHashMap<>();
    private GuiLayout() {}

    static void forget(Inventory inventory) {
        ACTIONS.remove(inventory);
        PAGES.remove(inventory);
        TITLES.remove(inventory);
    }

    static int action(Inventory inventory, int slot) {
        return ACTIONS.getOrDefault(inventory, Map.of()).getOrDefault(slot, -1);
    }

    static boolean navigation(InventoryClickEvent event) {
        Runnable[] pages = PAGES.get(event.getView().getTopInventory());
        int action = action(event.getView().getTopInventory(), event.getRawSlot());
        if (action == -4) {
            event.setCancelled(true);
            event.getWhoClicked().closeInventory();
            return true;
        }
        if (pages == null) return false;
        if (action == -2 || action == -3) {
            event.setCancelled(true);
            pages[action == -2 ? 0 : 1].run();
            return true;
        }
        return false;
    }

    static void open(Player player, Inventory template, LandWardPlugin plugin) {
        Map<Integer, Integer> positions = new LinkedHashMap<>();
        int size = template.getSize();
        Object holder = template.getHolder();
        boolean map = holder instanceof LandMapHolder;
        if (map) {
            for (int i = 0; i < size; i++) positions.put(i, i);
            positions.remove(45); // Bottom-left navigation replaces this one map tile.
            positions.put(53, 45);
        } else {
            for (int i = 0; i < size; i++) {
                ItemStack item = template.getItem(i);
                if (item != null && item.getType() != Material.BLACK_STAINED_GLASS_PANE) positions.put(i, i);
            }
            if (holder instanceof LandManagementHolder management) {
                switch (management.screen()) {
                    case SETTINGS -> {
                        size = 36;
                        positions.put(37, 28);
                        positions.put(40, 31);
                        positions.put(43, 34);
                    }
                    case WELCOME -> { positions.put(13, 11); positions.put(22, 13); }
                    case MAIN -> positions.put(17, 24);
                    case MEMBER_MAIN -> { size = 18; positions.put(4, 4); positions.put(26, 9); }
                    default -> {}
                }
            }
            if (holder instanceof ClaimAdminHolder admin) {
                switch (admin.screen()) {
                    case ADMIN_SETTINGS -> {
                        size = 36;
                        positions.put(37, 28);
                        positions.put(40, 31);
                        positions.put(19, 20);
                        positions.put(20, 23);
                        positions.put(21, 24);
                    }
                    case MAIN -> { positions.put(9, 10); positions.put(11, 12); positions.put(13, 14); positions.put(15, 22); positions.put(17, 16); positions.put(20, 24); }
                    case PLAYER_CLAIM_DETAILS -> { positions.put(18, 22); positions.put(20, 24); }
                    default -> {}
                }
            }
            int back = template.getSize() - 1;
            if (positions.containsKey(back)) positions.put(back, size - 9);
        }
        Inventory display = Bukkit.createInventory(template.getHolder(), size, playerTitle(template));
        Map<Integer, Integer> actions = new HashMap<>();
        fill(display);
        positions.forEach((source, target) -> {
            ItemStack item = template.getItem(source);
            if (item != null) { display.setItem(target, item); actions.put(target, source); }
        });
        if (!map) close(display, actions, player, plugin);
        ACTIONS.put(display, actions);
        bind(template, display);
        player.openInventory(display);
    }

    private static void close(Inventory inventory, Map<Integer, Integer> actions, Player player, LandWardPlugin plugin) {
        int slot = inventory.getSize() - 1;
        String locale = plugin.localeService().localeFor(player);
        inventory.setItem(slot, GuiText.item(Material.BARRIER,
                plugin.localeService().message(locale, "gui.close"), NamedTextColor.RED, List.of()));
        actions.put(slot, -4);
    }

    // Inventory does not expose its title; constructors register the exact translated title.
    private static final Map<Inventory, String> TITLES = new WeakHashMap<>();
    static void title(Inventory inventory, String title) { TITLES.put(inventory, title); }
    private static String playerTitle(Inventory inventory) { return Objects.requireNonNull(TITLES.get(inventory)); }

    static void fill(Inventory inventory) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        meta.setHideTooltip(true);
        pane.setItemMeta(meta);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, pane);
    }

    private static void bind(Inventory template, Inventory display) {
        if (template.getHolder() instanceof LandManagementHolder holder) holder.inventory(display);
        else if (template.getHolder() instanceof ClaimAdminHolder holder) holder.inventory(display);
        else if (template.getHolder() instanceof LandMapHolder holder) holder.setInventory(display);
        else if (template.getHolder() instanceof UnclaimConfirmationHolder holder) holder.setInventory(display);
    }

    static final class Entries {
        private final List<ItemStack> items = new ArrayList<>();
        private final List<IntConsumer> bindings = new ArrayList<>();
        void add(ItemStack item, IntConsumer binding) { items.add(item); bindings.add(binding); }
        void open(Player player, Inventory template, LandWardPlugin plugin) { show(player, template, plugin, 0); }
        private void show(Player player, Inventory template, LandWardPlugin plugin, int page) {
            int count = Math.min(45, items.size() - page * 45);
            int size = GuiGrid.size(count);
            Inventory display = Bukkit.createInventory(template.getHolder(), size, playerTitle(template));
            fill(display);
            Map<Integer, Integer> actions = new HashMap<>();
            for (int i = 0; i < count; i++) {
                int index = page * 45 + i;
                int logical = 100 + index;
                int slot = GuiGrid.slot(i, count);
                bindings.get(index).accept(logical);
                display.setItem(slot, items.get(index));
                actions.put(slot, logical);
            }
            if (items.isEmpty() && template.getItem(22) != null) display.setItem(4, template.getItem(22));
            display.setItem(size - 9, template.getItem(53));
            actions.put(size - 9, 53);
            String locale = plugin.localeService().localeFor(player);
            if (page > 0) {
                display.setItem(size - 6, GuiText.item(Material.ARROW, plugin.localeService().message(locale, "gui.previous-page"), NamedTextColor.YELLOW, List.of()));
                actions.put(size - 6, -2);
            }
            if ((page + 1) * 45 < items.size()) {
                display.setItem(size - 4, GuiText.item(Material.ARROW, plugin.localeService().message(locale, "gui.next-page"), NamedTextColor.YELLOW, List.of()));
                actions.put(size - 4, -3);
            }
            ACTIONS.put(display, actions);
            close(display, actions, player, plugin);
            PAGES.put(display, new Runnable[]{() -> show(player, template, plugin, page - 1), () -> show(player, template, plugin, page + 1)});
            bind(template, display);
            player.openInventory(display);
        }
    }
}
