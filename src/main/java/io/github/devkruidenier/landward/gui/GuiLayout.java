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
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;

/** Presentation mapping keeps domain actions independent of displayed slot positions. */
final class GuiLayout {
    private static final Map<Inventory, Map<Integer, Integer>> ACTIONS = new WeakHashMap<>();
    private static final Map<Inventory, Runnable[]> PAGES = new WeakHashMap<>();
    private static final Map<UUID, Map<String, Integer>> LAST_PAGES = new HashMap<>();
    private GuiLayout() {}

    static void forgetPlayer(UUID player) { LAST_PAGES.remove(player); }

    private static String pageKey(Inventory inventory) {
        if (inventory.getHolder() instanceof ClaimAdminHolder holder)
            return "admin:" + holder.screen() + ":" + holder.selectedLandId();
        if (inventory.getHolder() instanceof LandManagementHolder holder)
            return "player:" + holder.screen() + ":" + holder.selectedLandId();
        return inventory.getType().name();
    }

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
            for (int i = 0; i < size - 9; i++) positions.put(i, i);
            positions.put(53, 45);
        } else {
            for (int i = 0; i < size; i++) {
                ItemStack item = template.getItem(i);
                if (item != null && item.getType() != Material.BLACK_STAINED_GLASS_PANE) positions.put(i, i);
            }
            String profile = holder instanceof LandManagementHolder management ? "player:" + management.screen()
                    : holder instanceof ClaimAdminHolder admin ? "admin:" + admin.screen() : "confirmation";
            size = GuiPositions.size(profile, size);
            positions.putAll(GuiPositions.overrides(profile));
            int back = template.getSize() - 1;
            if (positions.containsKey(back)) positions.put(back, size - 9);
        }
        Inventory display = Bukkit.createInventory(template.getHolder(), size, playerTitle(template));
        Map<Integer, Integer> actions = new HashMap<>();
        fill(display);
        Set<Integer> present = new HashSet<>();
        positions.keySet().forEach(source -> { if (template.getItem(source) != null) present.add(source); });
        Map<Integer, Integer> rendered = map ? positions : GuiPositions.center(positions, present, size);
        rendered.forEach((source, target) -> {
            ItemStack item = template.getItem(source);
            if (item != null) { display.setItem(target, item); actions.put(target, source); }
        });
        close(display, actions, player, plugin);
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
        private final List<Supplier<ItemStack>> items = new ArrayList<>();
        private final List<BooleanSupplier> valid = new ArrayList<>();
        private final List<IntConsumer> bindings = new ArrayList<>();
        void add(Supplier<ItemStack> item, IntConsumer binding) { add(item, binding, () -> true); }
        void add(Supplier<ItemStack> item, IntConsumer binding, BooleanSupplier exists) {
            items.add(item); bindings.add(binding); valid.add(exists);
        }
        void open(Player player, Inventory template, LandWardPlugin plugin) {
            show(player, template, plugin, LAST_PAGES.getOrDefault(player.getUniqueId(), Map.of())
                    .getOrDefault(pageKey(template), 0));
        }
        private void show(Player player, Inventory template, LandWardPlugin plugin, int page) {
            List<Integer> visible = java.util.stream.IntStream.range(0, items.size())
                    .filter(index -> valid.get(index).getAsBoolean()).boxed().toList();
            int currentPage = GuiGrid.page(page, visible.size());
            LAST_PAGES.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                    .put(pageKey(template), currentPage);
            int count = GuiGrid.count(currentPage, visible.size());
            int size = GuiGrid.size(count);
            Inventory display = Bukkit.createInventory(template.getHolder(), size, playerTitle(template));
            fill(display);
            Map<Integer, Integer> actions = new HashMap<>();
            for (int i = 0; i < count; i++) {
                int index = visible.get(GuiGrid.start(currentPage, visible.size()) + i);
                int logical = 100 + index;
                int slot = GuiGrid.slot(i, count);
                bindings.get(index).accept(logical);
                display.setItem(slot, items.get(index).get());
                actions.put(slot, logical);
            }
            if (visible.isEmpty() && template.getItem(22) != null) display.setItem(4, template.getItem(22));
            display.setItem(size - 9, template.getItem(53));
            actions.put(size - 9, 53);
            String locale = plugin.localeService().localeFor(player);
            if (currentPage > 0) {
                display.setItem(size - 6, GuiText.item(Material.RED_STAINED_GLASS_PANE, plugin.localeService().message(locale, "gui.previous-page"), NamedTextColor.RED, List.of()));
                actions.put(size - 6, -2);
            }
            if (currentPage + 1 < GuiGrid.pages(visible.size())) {
                display.setItem(size - 4, GuiText.item(Material.BLUE_STAINED_GLASS_PANE, plugin.localeService().message(locale, "gui.next-page"), NamedTextColor.BLUE, List.of()));
                actions.put(size - 4, -3);
            }
            ACTIONS.put(display, actions);
            close(display, actions, player, plugin);
            PAGES.put(display, new Runnable[]{() -> show(player, template, plugin, currentPage - 1), () -> show(player, template, plugin, currentPage + 1)});
            bind(template, display);
            player.openInventory(display);
        }
    }
}
