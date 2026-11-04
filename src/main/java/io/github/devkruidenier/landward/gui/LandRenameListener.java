package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.command.LandCommand;
import io.github.devkruidenier.landward.storage.StorageException;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.view.AnvilView;

/** Provides native anvil text entry for both creating and renaming a land. */
public final class LandRenameListener implements Listener {
    private final LandWardPlugin plugin;
    private final LandManager landManager;
    private final LandCommand command;

    public LandRenameListener(LandWardPlugin plugin, LandManager landManager, LandCommand command) {
        this.plugin = plugin;
        this.landManager = landManager;
        this.command = command;
    }

    public void begin(Player player) {
        openAnvil(player, false);
    }

    public void beginCreate(Player player) {
        openAnvil(player, true);
    }

    private void openAnvil(Player player, boolean creating) {
        RenameAnvilHolder holder = new RenameAnvilHolder(creating);
        String locale = plugin.localeService().localeFor(player);
        Inventory inventory = Bukkit.createInventory(holder, InventoryType.ANVIL,
                Component.text(plugin.localeService().message(locale, creating ? "gui.welcome-title" : "gui.management-rename")));
        holder.inventory(inventory);
        ItemStack input = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = input.getItemMeta();
        String currentName = creating
                ? plugin.localeService().message(locale, "gui.welcome-create")
                : landManager.ownedBy(player.getUniqueId()).map(land -> land.name()).orElse("");
        meta.displayName(Component.text(currentName));
        input.setItemMeta(meta);
        inventory.setItem(0, input);
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getInventory().getHolder() instanceof RenameAnvilHolder holder)) return;
        String name = event.getView().getRenameText();
        if (name == null || name.isBlank()) {
            event.setResult(null);
            return;
        }
        ItemStack result = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = result.getItemMeta();
        meta.displayName(Component.text(name));
        result.setItemMeta(meta);
        event.setResult(result);
        if (!holder.creating()) {
            int cost = renameCost();
            event.getView().setMaximumRepairCost(Math.max(40, cost));
            event.getView().setRepairCost(cost);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvilClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RenameAnvilHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() != 2) return;
        RenameAnvilHolder holder = (RenameAnvilHolder) event.getView().getTopInventory().getHolder();
        AnvilView inventory = (AnvilView) event.getView();
        String name = inventory.getRenameText();
        if (name == null || name.isBlank()) return;
        if (holder.creating()) {
            player.closeInventory();
            create(player, name);
            return;
        }
        int cost = renameCost();
        if (player.getLevel() < cost) {
            player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "land.rename-not-enough-xp", java.util.Map.of("cost", cost)));
            return;
        }
        if (rename(player, name) && cost > 0) {
            player.setLevel(player.getLevel() - cost);
        }
        player.closeInventory();
    }

    private void create(Player player, String name) {
        try {
            command.createFromMenu(player, name);
        } catch (StorageException exception) {
            plugin.getLogger().warning("Could not create land from GUI: " + exception.getMessage());
            player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "general.storage-error"));
        }
    }

    private boolean rename(Player player, String name) {
        try {
            LandManager.RenameResult result = landManager.rename(player.getUniqueId(), name);
            String key = switch (result.status()) {
                case RENAMED -> "land.renamed";
                case UNCHANGED -> "land.rename-unchanged";
                case NO_LAND -> "claim.no-land";
                case INVALID_NAME -> "land.invalid-name";
                case NAME_TAKEN -> "land.name-taken";
            };
            player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), key,
                    java.util.Map.of("name", result.land() == null ? "-" : result.land().name())));
            if (result.status() == LandManager.RenameStatus.RENAMED) plugin.syncMapIntegrations();
            return result.status() == LandManager.RenameStatus.RENAMED;
        } catch (StorageException exception) {
            plugin.getLogger().warning("Could not rename land from GUI: " + exception.getMessage());
            player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "general.storage-error"));
            return false;
        }
    }

    private int renameCost() {
        return Math.max(0, plugin.getConfig().getInt("rename.experience-level-cost", 0));
    }
}
