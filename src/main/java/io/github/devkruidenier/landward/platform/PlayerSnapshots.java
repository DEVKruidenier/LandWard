package io.github.devkruidenier.landward.platform;

import io.github.devkruidenier.landward.model.ChunkPosition;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Immutable values for asynchronous placeholders; never reads another region's live player. */
public final class PlayerSnapshots implements Listener {
    public record Snapshot(ChunkPosition chunk, String locale) {}
    private final Map<UUID, Snapshot> values = new ConcurrentHashMap<>();
    public Snapshot get(UUID player) { return values.get(player); }
    public void capture(Player player) { capture(player, player.getLocation()); }
    private void capture(Player player, Location location) {
        if (location.getWorld() == null) return;
        values.put(player.getUniqueId(), new Snapshot(new ChunkPosition(location.getWorld().getUID(),
                location.getBlockX() >> 4, location.getBlockZ() >> 4), player.getLocale()));
    }
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent event) { capture(event.getPlayer()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void move(PlayerMoveEvent event) {
        if (event.getTo()!=null) capture(event.getPlayer(), event.getTo());
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent event) {
        if (event.getTo()!=null) capture(event.getPlayer(), event.getTo());
    }
    @EventHandler(priority=EventPriority.MONITOR) public void locale(PlayerLocaleChangeEvent event) {
        values.computeIfPresent(event.getPlayer().getUniqueId(), (id, old) -> new Snapshot(old.chunk(), event.getLocale()));
    }
    @EventHandler public void quit(PlayerQuitEvent event) { values.remove(event.getPlayer().getUniqueId()); }
    public void clear() { values.clear(); }
}
