package io.github.devkruidenier.landward.update;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.console.LandWardConsole.Tone;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import java.util.Map;

/** Localized update summaries without bypassing world protection or installing a running JAR. */
public final class UpdateNotifications implements Listener {
    private static final String BORDER = "========================================";
    private final LandWardPlugin plugin;
    public UpdateNotifications(LandWardPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("landward.pladmin") || !plugin.getConfig().getBoolean("updates.notify-on-join", true)) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !player.hasPermission("landward.pladmin")) return;
            plugin.updateChecker().refresh().thenAccept(status -> {
                if (!plugin.isEnabled()) return;
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline() && player.hasPermission("landward.pladmin")
                            && plugin.getConfig().getBoolean("updates.enabled", true)
                            && plugin.getConfig().getBoolean("updates.notify-on-join", true)
                            && status.state() == UpdateChecker.State.OUTDATED) player(player, status);
                });
            });
        }, 40L);
    }

    public void console(UpdateChecker.UpdateStatus status) {
        plugin.console().info(BORDER, Tone.WARNING);
        plugin.console().info(plugin.localeService().console("updates.notice-title"), Tone.WARNING);
        plugin.console().info(plugin.localeService().console("updates.notice-current"),
                Map.of("version", plugin.getDescription().getVersion()), Tone.ERROR);
        plugin.console().info(plugin.localeService().console("updates.notice-latest"), Map.of("version", status.latestVersion()), Tone.SUCCESS);
        plugin.console().info(plugin.localeService().console("updates.notice-download"), Tone.WARNING);
        plugin.console().info(UpdateChecker.RELEASES_PAGE, Tone.BRAND);
        plugin.console().info(BORDER, Tone.WARNING);
    }

    private void player(Player player, UpdateChecker.UpdateStatus status) {
        String locale = plugin.localeService().localeFor(player);
        player.sendMessage(ChatColor.GOLD + BORDER);
        player.sendMessage(ChatColor.YELLOW + plugin.localeService().message(locale, "updates.notice-title"));
        player.sendMessage(ChatColor.YELLOW + plugin.localeService().message(locale, "updates.notice-current",
                Map.of("version", ChatColor.RED + plugin.getDescription().getVersion())));
        player.sendMessage(ChatColor.YELLOW + plugin.localeService().message(locale, "updates.notice-latest",
                Map.of("version", ChatColor.GREEN + status.latestVersion())));
        player.sendMessage(ChatColor.YELLOW + plugin.localeService().message(locale, "updates.notice-download"));
        TextComponent link = new TextComponent(UpdateChecker.RELEASES_PAGE);
        link.setColor(net.md_5.bungee.api.ChatColor.AQUA);
        link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, UpdateChecker.RELEASES_PAGE));
        player.spigot().sendMessage(link);
        player.sendMessage(ChatColor.GOLD + BORDER);
    }
}
