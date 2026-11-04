package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.command.LandCommand;
import io.github.devkruidenier.landward.storage.StorageException;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Lets the member-management GUI safely collect a new land name through chat. */
public final class LandRenameListener implements Listener {
    private final LandWardPlugin plugin;
    private final LandManager landManager;
    private final LandCommand command;
    private final Set<UUID> awaitingNames = ConcurrentHashMap.newKeySet();
    private final Set<UUID> awaitingCreationNames = ConcurrentHashMap.newKeySet();

    public LandRenameListener(LandWardPlugin plugin, LandManager landManager, LandCommand command) {
        this.plugin = plugin;
        this.landManager = landManager;
        this.command = command;
    }

    public void begin(Player player) {
        awaitingNames.add(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), "gui.rename-prompt"));
    }

    public void beginCreate(Player player) {
        awaitingCreationNames.add(player.getUniqueId());
        player.closeInventory();
        player.sendMessage("Type your new land name in chat (3-24 characters).");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        boolean creating = awaitingCreationNames.remove(player.getUniqueId());
        if (!creating && !awaitingNames.remove(player.getUniqueId())) return;
        event.setCancelled(true);
        String name = PlainTextComponentSerializer.plainText().serialize(event.message());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (creating) create(player, name);
            else rename(player, name);
        });
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
