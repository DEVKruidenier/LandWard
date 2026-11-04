package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.command.LandCommand;
import io.github.devkruidenier.landward.storage.StorageException;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.inventory.InventoryType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Lets the member-management GUI safely collect a new land name through chat. */
public final class LandRenameListener implements Listener {
    private final LandWardPlugin plugin;
    private final LandManager landManager;
    private final LandCommand command;
    private final Set<UUID> awaitingCreationNames = ConcurrentHashMap.newKeySet();

    public LandRenameListener(LandWardPlugin plugin, LandManager landManager, LandCommand command) {
        this.plugin = plugin;
        this.landManager = landManager;
        this.command = command;
    }

    public void begin(Player player) {
        RenameAnvilHolder holder = new RenameAnvilHolder();
        String locale = plugin.localeService().localeFor(player);
        Inventory inventory = Bukkit.createInventory(holder, InventoryType.ANVIL,
                Component.text(plugin.localeService().message(locale, "gui.management-rename")));
        holder.inventory(inventory);
        ItemStack input = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = input.getItemMeta();
        String currentName = landManager.ownedBy(player.getUniqueId()).map(land -> land.name()).orElse("");
        meta.displayName(Component.text(currentName));
        input.setItemMeta(meta);
        inventory.setItem(0, input);
        player.openInventory(inventory);
    }

    public void beginCreate(Player player) {
        awaitingCreationNames.add(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "gui.create-prompt"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        boolean creating = awaitingCreationNames.remove(player.getUniqueId());
        if (!creating) return;
        event.setCancelled(true);
        String name = PlainTextComponentSerializer.plainText().serialize(event.message());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            create(player, name);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getInventory().getHolder() instanceof RenameAnvilHolder)) return;
        String name = event.getInventory().getRenameText();
        if (name == null || name.isBlank()) {
            event.setResult(null);
            return;
        }
        ItemStack result = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = result.getItemMeta();
        meta.displayName(Component.text(name));
        result.setItemMeta(meta);
        event.setResult(result);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvilClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RenameAnvilHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() != 2) return;
        AnvilInventory inventory = (AnvilInventory) event.getView().getTopInventory();
        String name = inventory.getRenameText();
        if (name == null || name.isBlank()) return;
        player.closeInventory();
        rename(player, name);
    }

    private void create(Player player, String name) {
        try {
            command.createFromMenu(player, name);
        } catch (StorageException exception) {
            plugin.getLogger().warning("Could not create land from GUI: " + exception.getMessage());
            player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "general.storage-error"));
        }
    }

    private void rename(Player player, String name) {
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
        } catch (StorageException exception) {
            plugin.getLogger().warning("Could not rename land from GUI: " + exception.getMessage());
            player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "general.storage-error"));
        }
    }
}
