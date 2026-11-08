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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Native anvil entry for an admin-only player-land ownership transfer. */
public final class AdminOwnerChangeListener implements Listener {
    private static final Pattern PLAYER_NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private final LandWardPlugin plugin;
    private final LandManager lands;
    private final Map<UUID, UUID> activeLandByAdmin = new ConcurrentHashMap<>();

    public AdminOwnerChangeListener(LandWardPlugin plugin, LandManager lands) { this.plugin = plugin; this.lands = lands; }

    public void begin(Player player, UUID landId) {
        activeLandByAdmin.put(player.getUniqueId(), landId);
        String locale = plugin.localeService().localeFor(player);
        Inventory anvil = Bukkit.createInventory(null, InventoryType.ANVIL,
                plugin.localeService().message(locale, "gui.admin-change-owner"));
        ItemStack input = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = input.getItemMeta();
        meta.setDisplayName(plugin.localeService().message(locale, "gui.admin-owner-input"));
        input.setItemMeta(meta);
        anvil.setItem(0, input);
        ItemStack confirm = new ItemStack(Material.LIME_DYE);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setDisplayName(plugin.localeService().message(locale, "gui.anvil-confirm"));
        confirm.setItemMeta(confirmMeta);
        anvil.setItem(1, confirm);
        player.openInventory(anvil);
    }

    @EventHandler
    public void onPrepare(PrepareAnvilEvent event) {
        if (!activeLandByAdmin.containsKey(event.getView().getPlayer().getUniqueId())) return;
        String entered = event.getView().getRenameText();
        if (entered == null || !PLAYER_NAME.matcher(entered.strip()).matches()) { event.setResult(null); return; }
        ItemStack result = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = result.getItemMeta(); meta.setDisplayName(entered.strip()); result.setItemMeta(meta);
        event.setResult(result);
        event.getView().setRepairCost(0);
        event.getView().setMaximumRepairCost(40);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getView().getType() != InventoryType.ANVIL) return;
        UUID landId = activeLandByAdmin.get(player.getUniqueId());
        if (landId == null) return;
        event.setCancelled(true);
        if (event.getRawSlot() != 1 && event.getRawSlot() != 2) return;
        String name = event.getView() instanceof AnvilView anvilView ? anvilView.getRenameText() : null;
        if (name != null) name = name.strip();
        if (name == null || name.isBlank()) name = name(event.getCurrentItem());
        if (name == null || !PLAYER_NAME.matcher(name).matches()) return;
        OfflinePlayer target = Bukkit.getOfflinePlayer(name);
        try {
            LandManager.TransferResult result = lands.transferOwnership(landId, target.getUniqueId());
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
        activeLandByAdmin.remove(player.getUniqueId());
        player.closeInventory();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player) || event.getView().getType() != InventoryType.ANVIL) return;
        if (activeLandByAdmin.remove(player.getUniqueId()) != null) clear(event.getView());
    }

    private String name(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return null;
        return item.getItemMeta().getDisplayName().strip();
    }

    private void clear(org.bukkit.inventory.InventoryView view) {
        view.getTopInventory().setItem(0, null); view.getTopInventory().setItem(1, null); view.getTopInventory().setItem(2, null);
    }
}
