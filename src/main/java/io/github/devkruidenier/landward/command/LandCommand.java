package io.github.devkruidenier.landward.command;

import io.github.devkruidenier.landward.LandWardPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LandCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT_COMPLETIONS = List.of("help");

    private final LandWardPlugin plugin;

    public LandCommand(LandWardPlugin plugin) {
        this.plugin = plugin;
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

        sender.sendMessage(message(sender, "general.unknown-command"));
        return true;
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
        sender.sendMessage(message(sender, "command.development"));
    }

    private String message(CommandSender sender, String key) {
        return plugin.localeService().message(plugin.localeService().localeFor(sender), key);
    }

    private String message(CommandSender sender, String key, Map<String, ?> placeholders) {
        return plugin.localeService().message(plugin.localeService().localeFor(sender), key, placeholders);
    }
}
