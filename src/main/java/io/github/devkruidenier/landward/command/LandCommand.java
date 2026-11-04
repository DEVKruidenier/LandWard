package io.github.devkruidenier.landward.command;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.model.Land;
import io.github.devkruidenier.landward.storage.StorageException;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class LandCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT_COMPLETIONS = List.of("help", "create", "claim", "info");

    private final LandWardPlugin plugin;
    private final LandManager landManager;

    public LandCommand(LandWardPlugin plugin, LandManager landManager) {
        this.plugin = plugin;
        this.landManager = landManager;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] arguments
    ) {
        if (arguments.length == 0 || (arguments.length == 1 && arguments[0].equalsIgnoreCase("help"))) {
            sendHelp(sender);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(message(sender, "general.player-only"));
            return true;
        }

        try {
            return switch (arguments[0].toLowerCase(Locale.ROOT)) {
                case "create" -> create(player, arguments);
                case "claim" -> claim(player);
                case "info" -> info(player);
                default -> {
                    sender.sendMessage(message(sender, "general.unknown-command"));
                    yield true;
                }
            };
        } catch (StorageException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Land command storage operation failed", exception);
            sender.sendMessage(message(sender, "general.storage-error"));
            return true;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] arguments
    ) {
        if (arguments.length != 1) {
            return List.of();
        }

        String prefix = arguments[0].toLowerCase(Locale.ROOT);
        return ROOT_COMPLETIONS.stream()
                .filter(completion -> completion.startsWith(prefix))
                .toList();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(message(sender, "command.header", Map.of(
                "version", plugin.getPluginMeta().getVersion()
        )));
        sender.sendMessage(message(sender, "command.description"));
        sender.sendMessage(message(sender, "command.help"));
        sender.sendMessage(message(sender, "command.help-create"));
        sender.sendMessage(message(sender, "command.help-claim"));
        sender.sendMessage(message(sender, "command.help-info"));
    }

    private boolean create(Player player, String[] arguments) throws StorageException {
        if (!worldEnabled(player)) {
            player.sendMessage(message(player, "claim.world-disabled"));
            return true;
        }
        if (arguments.length < 2) {
            player.sendMessage(message(player, "land.create-usage"));
            return true;
        }

        String name = String.join(" ", java.util.Arrays.copyOfRange(arguments, 1, arguments.length));
        LandManager.CreateResult result = landManager.create(player.getUniqueId(), name, position(player));
        String key = switch (result.status()) {
            case CREATED -> "land.created";
            case INVALID_NAME -> "land.invalid-name";
            case ALREADY_OWNER -> "land.already-owner";
            case NAME_TAKEN -> "land.name-taken";
            case CHUNK_ALREADY_CLAIMED -> "land.chunk-already-claimed";
        };
        player.sendMessage(message(player, key, Map.of(
                "name", result.land() == null ? name : result.land().name()
        )));
        return true;
    }

    private boolean claim(Player player) throws StorageException {
        if (!worldEnabled(player)) {
            player.sendMessage(message(player, "claim.world-disabled"));
            return true;
        }

        ChunkPosition position = position(player);
        int maximum = plugin.getConfig().getInt("claims.maximum-chunks-per-land", 25);
        boolean adjacent = plugin.getConfig().getBoolean("claims.require-adjacent-chunks", true);
        LandManager.ClaimResult result = landManager.claim(player.getUniqueId(), position, maximum, adjacent);
        String key = switch (result.status()) {
            case CLAIMED -> "claim.claimed";
            case NO_LAND -> "claim.no-land";
            case ALREADY_YOURS -> "claim.already-yours";
            case ALREADY_CLAIMED -> "claim.already-claimed";
            case LIMIT_REACHED -> "claim.limit-reached";
            case NOT_ADJACENT -> "claim.not-adjacent";
        };
        player.sendMessage(message(player, key, Map.of(
                "name", result.land() == null ? "-" : result.land().name(),
                "count", result.claimCount(),
                "limit", maximum
        )));
        return true;
    }

    private boolean info(Player player) {
        Optional<Land> land = landManager.landAt(position(player));
        if (land.isEmpty()) {
            player.sendMessage(message(player, "claim.unclaimed"));
            return true;
        }
        Land claimedLand = land.get();
        player.sendMessage(message(player, "claim.info", Map.of(
                "name", claimedLand.name(),
                "count", landManager.claimCount(claimedLand.id())
        )));
        return true;
    }

    private ChunkPosition position(Player player) {
        return new ChunkPosition(
                player.getWorld().getUID(),
                player.getLocation().getChunk().getX(),
                player.getLocation().getChunk().getZ()
        );
    }

    private boolean worldEnabled(Player player) {
        String world = player.getWorld().getName();
        List<String> enabled = plugin.getConfig().getStringList("claims.enabled-worlds");
        List<String> disabled = plugin.getConfig().getStringList("claims.disabled-worlds");
        return !disabled.contains(world) && (enabled.isEmpty() || enabled.contains(world));
    }

    private String message(CommandSender sender, String key) {
        return plugin.localeService().message(plugin.localeService().localeFor(sender), key);
    }

    private String message(CommandSender sender, String key, Map<String, ?> placeholders) {
        return plugin.localeService().message(plugin.localeService().localeFor(sender), key, placeholders);
    }
}
