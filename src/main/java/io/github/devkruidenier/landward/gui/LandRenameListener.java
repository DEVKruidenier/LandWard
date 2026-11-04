package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.command.LandCommand;
import io.github.devkruidenier.landward.storage.StorageException;
import io.papermc.paper.math.Position;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Uses Paper's virtual sign editor as a native text-input GUI. Unlike a vanilla
 * anvil it has no client-side enchantment cost and never consumes XP by itself.
 */
public final class LandRenameListener implements Listener {
    private final LandWardPlugin plugin;
    private final LandManager landManager;
    private final LandCommand command;
    private final Map<UUID, InputMode> pendingInput = new ConcurrentHashMap<>();

    public LandRenameListener(LandWardPlugin plugin, LandManager landManager, LandCommand command) {
        this.plugin = plugin;
        this.landManager = landManager;
        this.command = command;
    }

    public void begin(Player player) {
        openSignInput(player, InputMode.RENAME);
    }

    public void beginCreate(Player player) {
        openSignInput(player, InputMode.CREATE);
    }

    private void openSignInput(Player player, InputMode mode) {
        pendingInput.put(player.getUniqueId(), mode);
        String locale = plugin.localeService().localeFor(player);
        player.sendMessage(plugin.localeService().message(locale,
                mode == InputMode.CREATE ? "gui.create-prompt" : "gui.rename-prompt"));
        // A client cannot open a virtual sign while a chest-style GUI is still open.
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (pendingInput.containsKey(player.getUniqueId()) && player.isOnline()) {
                player.openVirtualSign(Position.block(player.getLocation()), Side.FRONT);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        InputMode mode = pendingInput.remove(player.getUniqueId());
        if (mode == null) return;
        event.setCancelled(true);

        String name = String.join(" ", event.lines().stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList());
        if (name.isBlank()) return;
        if (mode == InputMode.CREATE) {
            create(player, name);
            return;
        }

        int cost = renameCost();
        if (player.getLevel() < cost) {
            player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player),
                    "land.rename-not-enough-xp", Map.of("cost", cost)));
            return;
        }
        if (rename(player, name) && cost > 0) {
            player.setLevel(player.getLevel() - cost);
        }
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
                    Map.of("name", result.land() == null ? "-" : result.land().name())));
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

    private enum InputMode { CREATE, RENAME }
}
