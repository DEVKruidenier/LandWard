package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.storage.StorageException;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Native anvil entry for an admin-only player-land ownership transfer. */
public final class AdminOwnerChangeListener implements Listener {
    private static final Pattern PLAYER_NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private final LandWardPlugin plugin;
    private final LandManager lands;
    public AdminOwnerChangeListener(LandWardPlugin plugin, LandManager lands) { this.plugin = plugin; this.lands = lands; }

    public void begin(Player player, UUID landId) {
        OwnerChangeAnvilHolder holder = new OwnerChangeAnvilHolder(landId);
        String locale = plugin.localeService().localeFor(player);
        Inventory anvil = Bukkit.createInventory(holder, InventoryType.ANVIL,
                plugin.localeService().message(locale, "gui.admin-change-owner"));
        holder.setInventory(anvil);
        ItemStack input = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = input.getItemMeta();
        meta.setDisplayName(" ");
        input.setItemMeta(meta);
        anvil.setItem(0, input);
        player.openInventory(anvil);
    }

    @EventHandler
    public void onPrepare(PrepareAnvilEvent event) {
        OwnerChangeAnvilHolder holder = holder(event.getView());
        if (holder == null) return;
        String entered = enteredName(event.getView());
        holder.setEnteredName(entered);
        if (entered == null || !PLAYER_NAME.matcher(entered).matches()) { event.setResult(null); return; }
        // Keep the typed player name in the native anvil result. An unrelated
        // result label can become Spigot's next rename-text value.
        ItemStack result = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = result.getItemMeta();
        meta.setDisplayName(entered);
        meta.setLore(List.of(plugin.localeService().message(
                plugin.localeService().localeFor(event.getView().getPlayer()), "gui.anvil-confirm")));
        result.setItemMeta(meta);
        event.setResult(result);
        event.getView().setRepairCost(0);
        event.getView().setMaximumRepairCost(40);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getView().getType() != InventoryType.ANVIL) return;
        OwnerChangeAnvilHolder holder = holder(event.getView());
        if (holder == null) return;
        event.setCancelled(true);
        if (event.getRawSlot() != 2) return;
        // Capture the native Anvil text before any close/clear operation. The
        // PrepareAnvilEvent cache can be stale when the output is rebuilt.
        String name = enteredName(event.getView());
        if (name == null || !PLAYER_NAME.matcher(name).matches()) return;
        OfflinePlayer target = Bukkit.getOfflinePlayer(name);
        try {
            LandManager.TransferResult result = lands.transferOwnership(holder.landId(), target.getUniqueId());
            String key = switch (result.status()) {
                case TRANSFERRED -> "admin.owner-changed";
                case UNCHANGED -> "admin.owner-unchanged";
                case NEW_OWNER_ALREADY_OWNS_LAND -> "admin.owner-already-owns";
                case NOT_PLAYER_LAND -> "admin.owner-invalid-land";
            };
            player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), key, Map.of("player", name)));
            if (result.status() == LandManager.TransferStatus.TRANSFERRED) {
                plugin.syncMapIntegrations();
                plugin.refreshAnalyticsIntegration();
            }
        } catch (StorageException exception) {
            plugin.getLogger().warning("Could not transfer land ownership: " + exception.getMessage());
            player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "general.storage-error"));
        }
        clear(event.getView());
        holder.setEnteredName(null);
        player.closeInventory();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (holder(event.getView()) != null) clear(event.getView());
    }

    private OwnerChangeAnvilHolder holder(org.bukkit.inventory.InventoryView view) {
        if (view.getType() != InventoryType.ANVIL) return null;
        return view.getTopInventory().getHolder() instanceof OwnerChangeAnvilHolder holder ? holder : null;
    }

    private String enteredName(org.bukkit.inventory.InventoryView view) {
        if (!(view instanceof AnvilView anvilView)) return null;
        return AnvilTextInput.capture(anvilView.getRenameText());
    }

    private void clear(org.bukkit.inventory.InventoryView view) {
        view.getTopInventory().setItem(0, null); view.getTopInventory().setItem(1, null); view.getTopInventory().setItem(2, null);
    }

    /** Identifies one ownership-transfer anvil session without global player state. */
    private static final class OwnerChangeAnvilHolder implements InventoryHolder {
        private final UUID landId;
        private Inventory inventory;
        private String enteredName;

        private OwnerChangeAnvilHolder(UUID landId) { this.landId = landId; }
        private UUID landId() { return landId; }
        private void setInventory(Inventory inventory) { this.inventory = inventory; }
        private void setEnteredName(String enteredName) { this.enteredName = enteredName; }
        private String enteredName() { return enteredName; }

        @Override
        public Inventory getInventory() {
            if (inventory == null) throw new IllegalStateException("Anvil inventory has not been created yet");
            return inventory;
        }
    }
}
