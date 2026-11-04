package io.github.devkruidenier.landward.command;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.gui.ClaimAdminGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Opens the administrator version of the local chunk map. Permission is also declared in plugin.yml. */
public final class ClaimAdminCommand implements CommandExecutor {
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
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(sender), "general.player-only"));
            return true;
        }
        new ClaimAdminGui(plugin, landManager).openMain(player);
        return true;
    }
}
