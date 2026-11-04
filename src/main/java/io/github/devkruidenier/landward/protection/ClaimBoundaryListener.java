package io.github.devkruidenier.landward.protection;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.model.Land;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;
import java.util.Optional;

/** Displays a single actionbar notification whenever a player crosses a claim boundary. */
public final class ClaimBoundaryListener implements Listener {
    private final LandWardPlugin plugin;
    private final LandManager landManager;

    public ClaimBoundaryListener(LandWardPlugin plugin, LandManager landManager) {
        this.plugin = plugin;
        this.landManager = landManager;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getConfig().getBoolean("notifications.claim-boundaries.enabled", true)) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || sameChunk(from, to)) return;
        Optional<Land> previous = landAt(from);
        Optional<Land> current = landAt(to);
        if (previous.map(Land::id).equals(current.map(Land::id))) return;

        Player player = event.getPlayer();
        String locale = plugin.localeService().localeFor(player);
        if (current.isPresent()) {
            Land land = current.get();
            actionbar(player, plugin.localeService().message(locale, "territory.entered", Map.of("name", land.name())), NamedTextColor.AQUA);
        } else if (previous.isPresent()) {
            actionbar(player, plugin.localeService().message(locale, "territory.left", Map.of("name", previous.get().name())), NamedTextColor.GRAY);
        }
    }

    private Optional<Land> landAt(Location location) {
        if (location.getWorld() == null || !plugin.isLandWorld(location.getWorld())) return Optional.empty();
        return landManager.landAt(new ChunkPosition(location.getWorld().getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4));
    }

    private boolean sameChunk(Location one, Location two) {
        return one.getWorld().equals(two.getWorld())
                && (one.getBlockX() >> 4) == (two.getBlockX() >> 4)
                && (one.getBlockZ() >> 4) == (two.getBlockZ() >> 4);
    }

    private void actionbar(Player player, String message, NamedTextColor color) {
        player.sendActionBar(Component.text(message, color));
    }
}
