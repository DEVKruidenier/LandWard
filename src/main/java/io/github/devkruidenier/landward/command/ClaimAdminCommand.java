package io.github.devkruidenier.landward.command;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.gui.ClaimAdminGui;
import io.github.devkruidenier.landward.storage.LandStorage;
import io.github.devkruidenier.landward.storage.StorageException;
import io.github.devkruidenier.landward.storage.migration.StorageMigrationService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Opens the administrator version of the local chunk map. Permission is also declared in plugin.yml. */
public final class ClaimAdminCommand implements CommandExecutor, TabCompleter {
    private final LandWardPlugin plugin;
    private final LandManager landManager;

    public ClaimAdminCommand(LandWardPlugin plugin, LandManager landManager) {
        this.plugin = plugin;
        this.landManager = landManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("landward.admin")) {
            sender.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(sender), "general.no-permission"));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadLandWard();
            sender.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(sender), "general.reloaded"));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("migrate")) {
            migrate(sender, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(sender), "general.player-only"));
            return true;
        }
        new ClaimAdminGui(plugin, landManager).openMain(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("reload", "migrate").stream().filter(value -> value.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("migrate")) return List.of("sqlite", "mysql").stream().filter(value -> value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT))).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("migrate") && "confirm".startsWith(args[2].toLowerCase(java.util.Locale.ROOT))) return List.of("confirm");
        return List.of();
    }

    private void migrate(CommandSender sender, String[] args) {
        String locale = plugin.localeService().localeFor(sender);
        if (args.length != 3 || !(args[1].equalsIgnoreCase("sqlite") || args[1].equalsIgnoreCase("mysql")) || !args[2].equalsIgnoreCase("confirm")) {
            sender.sendMessage(plugin.localeService().message(locale, "admin.migration-usage"));
            return;
        }
        if (sender instanceof Player || !plugin.getServer().getOnlinePlayers().isEmpty()) {
            sender.sendMessage(plugin.localeService().message(locale, "admin.migration-online"));
            return;
        }
        String targetType = args[1].toLowerCase(java.util.Locale.ROOT);
        if (plugin.storage().type().equalsIgnoreCase(targetType)) {
            sender.sendMessage(plugin.localeService().message(locale, "admin.migration-same-type"));
            return;
        }
        LandStorage target = null;
        try {
            target = plugin.createStorage(targetType);
            StorageMigrationService.MigrationResult result = new StorageMigrationService(plugin.getDataFolder().toPath().resolve("migration-backups"))
                    .migrate(plugin.storage(), target);
            plugin.getConfig().set("storage.type", targetType);
            plugin.saveConfig();
            sender.sendMessage(plugin.localeService().message(locale, "admin.migration-complete", java.util.Map.of(
                    "backup", result.backup().getFileName(), "lands", result.lands(), "claims", result.claims(), "members", result.members()
            )));
        } catch (StorageException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Storage migration failed: " + exception.getMessage());
            sender.sendMessage(plugin.localeService().message(locale, "admin.migration-failed"));
        } finally {
            if (target != null) {
                try { target.close(); }
                catch (StorageException exception) { plugin.getLogger().warning("Could not close migration target: " + exception.getMessage()); }
            }
        }
    }
}
