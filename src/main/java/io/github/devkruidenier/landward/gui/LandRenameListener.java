package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.command.LandCommand;
import io.github.devkruidenier.landward.storage.StorageException;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import org.bukkit.inventory.MenuType;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Provides native anvil text entry for both creating and renaming a land. */
public final class LandRenameListener implements Listener {
    private final LandWardPlugin plugin;
    private final LandManager landManager;
    private final LandCommand command;
    private final Map<UUID, RenameAnvilHolder> sessions = new HashMap<>();
    public LandRenameListener(LandWardPlugin plugin, LandManager landManager, LandCommand command) {
        this.plugin = plugin;
        this.landManager = landManager;
        this.command = command;
    }

    public void begin(Player player) { Bukkit.getScheduler().runTask(plugin, () -> openAnvil(player, false)); }
    public void beginCreate(Player player) { Bukkit.getScheduler().runTask(plugin, () -> openAnvil(player, true)); }

    private void openAnvil(Player player, boolean creating) {
        RenameAnvilHolder holder = new RenameAnvilHolder(creating);
        String locale = plugin.localeService().localeFor(player);
        AnvilView view = NativeAnvilInput.create(player,
                plugin.localeService().message(locale, creating ? "gui.create-anvil-title" : "gui.management-rename"));
        player.closeInventory();
        Inventory anvil = view.getTopInventory();
        holder.setInventory(anvil);
        sessions.put(player.getUniqueId(), holder);
        ItemStack input = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = input.getItemMeta();
        // A single whitespace seed gives creation a truly empty text field.
        // The native AnvilView text is the only accepted source of a name.
        String currentName = creating ? " "
                : landManager.ownedBy(player.getUniqueId()).map(land -> land.name()).orElse(" ");
        meta.setDisplayName(currentName);
        input.setItemMeta(meta);
        anvil.setItem(0, input);
        // Slot 1 is a real anvil input slot. It must remain empty: putting a
        // Confirm item there made its display name look like player input on
        // Spigot. The generated output in slot 2 is the only confirm action.
        player.openInventory(view);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        RenameAnvilHolder holder = holder(event.getView());
        if (holder == null) return;
        String name = enteredName(event.getView());
        holder.setEnteredName(name);
        if (name == null) {
            event.setResult(null);
            return;
        }
        // The anvil result must keep the typed name as its display name. On
        // Spigot, giving this item an unrelated label (for example "Confirm")
        // can become the view's subsequent rename text. The action label lives
        // in lore, which is presentation-only.
        ItemStack result = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = result.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(plugin.localeService().message(
                plugin.localeService().localeFor(event.getView().getPlayer()), "gui.anvil-confirm"),
                plugin.localeService().message(plugin.localeService().localeFor(event.getView().getPlayer()), "gui.hint-anvil")));
        result.setItemMeta(meta);
        event.setResult(result);
        configureRepairCost(event.getView(), holder.creating() ? 0 : renameCost());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvilClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        RenameAnvilHolder holder = holder(event.getView());
        if (holder == null) return;
        event.setCancelled(true);
        if (event.getRawSlot() != 2) return;
        // Read the native Anvil text at the moment of submission. The value
        // cached by PrepareAnvilEvent may be stale after the result item is
        // rebuilt; it is never an authoritative fallback for a click.
        String name = enteredName(event.getView());
        if (name == null) return;
        if (holder.creating()) {
            finish(event.getView());
            player.closeInventory();
            create(player, name);
            return;
        }
        int cost = renameCost();
        if (player.getLevel() < cost) {
            player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "land.rename-not-enough-xp", Map.of("cost", cost)));
            return;
        }
        if (rename(player, name)) {
            if (cost > 0) player.setLevel(player.getLevel() - cost);
            finish(event.getView());
            player.closeInventory();
        }
    }

    @EventHandler
    public void onAnvilClose(InventoryCloseEvent event) {
        if (holder(event.getView()) == null) return;
        clearTemporaryItems(event.getView());
        sessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (holder(event.getView()) != null) event.setCancelled(true);
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

    private RenameAnvilHolder holder(InventoryView view) {
        if (view.getType() != InventoryType.ANVIL) return null;
        RenameAnvilHolder session = sessions.get(view.getPlayer().getUniqueId());
        return session != null && session.getInventory().equals(view.getTopInventory()) ? session : null;
    }

    private String enteredName(InventoryView view) {
        if (!(view instanceof AnvilView anvilView)) return null;
        return AnvilTextInput.capture(anvilView.getRenameText());
    }

    private void finish(InventoryView view) {
        clearTemporaryItems(view);
        RenameAnvilHolder holder = holder(view);
        if (holder != null) holder.setEnteredName(null);
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
