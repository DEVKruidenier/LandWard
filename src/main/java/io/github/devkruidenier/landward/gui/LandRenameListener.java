package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.command.LandCommand;
import io.github.devkruidenier.landward.storage.StorageException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Provides native anvil text entry for both creating and renaming a land. */
public final class LandRenameListener implements Listener {
    private final LandWardPlugin plugin;
    private final LandManager landManager;
    private final LandCommand command;
    private final Map<UUID, String> enteredNames = new ConcurrentHashMap<>();
    private final Map<UUID, RenameAnvilHolder> activeAnvils = new ConcurrentHashMap<>();

    public LandRenameListener(LandWardPlugin plugin, LandManager landManager, LandCommand command) {
        this.plugin = plugin;
        this.landManager = landManager;
        this.command = command;
    }

    public void begin(Player player) { openAnvil(player, false); }
    public void beginCreate(Player player) { openAnvil(player, true); }

    private void openAnvil(Player player, boolean creating) {
        RenameAnvilHolder holder = new RenameAnvilHolder(creating);
        activeAnvils.put(player.getUniqueId(), holder);
        String locale = plugin.localeService().localeFor(player);
        InventoryView inventory = player.openAnvil(null, true);
        inventory.setTitle(plugin.localeService().message(locale, creating ? "gui.welcome-title" : "gui.management-rename"));
        ItemStack input = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = input.getItemMeta();
        String currentName = creating ? plugin.localeService().message(locale, "gui.welcome-create")
                : landManager.ownedBy(player.getUniqueId()).map(land -> land.name()).orElse("");
        meta.displayName(Component.text(currentName));
        input.setItemMeta(meta);
        inventory.getTopInventory().setItem(0, input);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        RenameAnvilHolder holder = activeAnvils.get(event.getView().getPlayer().getUniqueId());
        if (holder == null) return;
        String name = event.getView().getRenameText();
        if (name == null || name.isBlank()) { event.setResult(null); return; }
        enteredNames.put(event.getView().getPlayer().getUniqueId(), name.strip());
        ItemStack result = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = result.getItemMeta();
        meta.displayName(Component.text(name));
        result.setItemMeta(meta);
        event.setResult(result);
        configureRepairCost(event.getView(), holder.creating() ? 0 : renameCost());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvilClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        RenameAnvilHolder holder = activeAnvils.get(player.getUniqueId());
        if (holder == null || event.getView().getType() != InventoryType.ANVIL) return;
        event.setCancelled(true);
        if (event.getRawSlot() != 2) return;
        String name = enteredNames.remove(player.getUniqueId());
        if (name == null || name.isBlank()) name = nameFromResult(event.getCurrentItem());
        if (name == null || name.isBlank()) return;
        // This tag is only a visual text-input seed. Clear it before closing the
        // vanilla container so Minecraft cannot return it to the player's inventory.
        clearTemporaryItems(event.getView());
        if (holder.creating()) { player.closeInventory(); create(player, name); return; }
        int cost = renameCost();
        if (player.getLevel() < cost) {
            player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "land.rename-not-enough-xp", Map.of("cost", cost)));
            return;
        }
        if (rename(player, name)) {
            if (cost > 0) player.setLevel(player.getLevel() - cost);
            activeAnvils.remove(player.getUniqueId());
            player.closeInventory();
        }
    }

    @EventHandler
    public void onAnvilClose(InventoryCloseEvent event) {
        if (event.getView().getType() != InventoryType.ANVIL || !(event.getPlayer() instanceof Player player)) return;
        if (!activeAnvils.containsKey(player.getUniqueId())) return;
        clearTemporaryItems(event.getView());
        activeAnvils.remove(player.getUniqueId());
        enteredNames.remove(player.getUniqueId());
    }

    /** Removes only LandWard's temporary anvil contents before vanilla returns inputs on close. */
    private void clearTemporaryItems(InventoryView view) {
        view.getTopInventory().setItem(0, null);
        view.getTopInventory().setItem(1, null);
        view.getTopInventory().setItem(2, null);
    }

    private void configureRepairCost(AnvilView view, int cost) {
        view.setMaximumRepairCost(Math.max(40, cost));
        view.setRepairCost(cost);
    }

    private String nameFromResult(ItemStack result) {
        if (result == null || result.getType().isAir() || !result.hasItemMeta()) return null;
        ItemMeta meta = result.getItemMeta();
        if (!meta.hasDisplayName()) return null;
        return PlainTextComponentSerializer.plainText().serialize(meta.displayName()).strip();
    }

    private void create(Player player, String name) {
        try { command.createFromMenu(player, name); }
        catch (StorageException exception) {
            plugin.getLogger().warning("Could not create land from GUI: " + exception.getMessage());
            player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "general.storage-error"));
        }
    }

    private boolean rename(Player player, String name) {
        try {
            LandManager.RenameResult result = landManager.rename(player.getUniqueId(), name);
            String key = switch (result.status()) {
                case RENAMED -> "land.renamed"; case UNCHANGED -> "land.rename-unchanged";
                case NO_LAND -> "claim.no-land"; case INVALID_NAME -> "land.invalid-name"; case NAME_TAKEN -> "land.name-taken";
            };
            player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), key,
                    Map.of("name", result.land() == null ? "-" : result.land().name())));
            if (result.status() == LandManager.RenameStatus.RENAMED) plugin.syncMapIntegrations();
            return result.status() == LandManager.RenameStatus.RENAMED;
        } catch (StorageException exception) {
            plugin.getLogger().warning("Could not rename land from GUI: " + exception.getMessage());
            player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "general.storage-error"));
            return false;
        }
    }

    private int renameCost() { return Math.max(0, plugin.getConfig().getInt("rename.experience-level-cost", 0)); }
}
