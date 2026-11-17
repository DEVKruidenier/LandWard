package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.EntityDamageEvent;
import java.util.*;

/** Main-thread warmups revalidate mutable authorization and destination on every tick. */
public final class WarpTeleportService implements Listener {
    private final LandWardPlugin plugin; private final LandManager lands;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private record Pending(UUID land, LandWarp warp, Location origin, boolean admin, long due) {}
    private long tick;
    public WarpTeleportService(LandWardPlugin plugin, LandManager lands) {
        this.plugin = plugin; this.lands = lands;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::advance, 1, 1);
    }
    public void begin(Player player, UUID land, UUID warp, boolean admin) {
        if (lands.land(land).isEmpty()) { tell(player, "warps.unavailable"); return; }
        var selected = lands.features(land).warps().stream().filter(value -> value.id().equals(warp)).findFirst().orElse(null);
        if (selected == null || !permitted(player, land, selected, admin) || !safe(selected)) { tell(player, "warps.unavailable"); return; }
        int delay = Math.max(0, plugin.getConfig().getInt(admin ? "teleportation.admin-warp-delay-seconds" : "teleportation.warp-delay-seconds", 2));
        pending.put(player.getUniqueId(), new Pending(land, selected, player.getLocation().clone(), admin, tick + delay * 20L));
        tell(player, "warps.warming");
        if (delay == 0) advance();
    }
    private boolean permitted(Player player, UUID land, LandWarp warp, boolean admin) {
        World world = Bukkit.getWorld(warp.worldId());
        return player.isOnline() && plugin.isLandWorld(player.getWorld()) && world != null && plugin.isLandWorld(world)
                && !plugin.combatTags().isTagged(player)
                && (admin ? player.hasPermission("landward.admin") : lands.canUseWarps(player.getUniqueId(), land))
                && lands.landAt(warp.chunk()).map(value -> value.id().equals(land)).orElse(false)
                && lands.features(land).warps().contains(warp);
    }
    private void advance() {
        tick++;
        for (var entry : List.copyOf(pending.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey()); Pending request = entry.getValue();
            if (player == null) { pending.remove(entry.getKey()); continue; }
            if (lands.land(request.land()).isEmpty() || moved(player.getLocation(), request.origin())
                    || !permitted(player, request.land(), request.warp(), request.admin())) { cancel(player); continue; }
            if (tick < request.due()) continue;
            pending.remove(entry.getKey());
            if (!safe(request.warp()) || !permitted(player, request.land(), request.warp(), request.admin())) {
                tell(player, "warps.unavailable"); continue;
            }
            LandWarp warp = request.warp();
            boolean teleported = player.teleport(new Location(Bukkit.getWorld(warp.worldId()), warp.x(), warp.y(), warp.z(), warp.yaw(), warp.pitch()));
            tell(player, teleported ? "warps.complete" : "warps.unavailable");
        }
    }
    static boolean moved(Location current, Location origin) {
        return !Objects.equals(current.getWorld(), origin.getWorld()) || current.getX() != origin.getX()
                || current.getY() != origin.getY() || current.getZ() != origin.getZ();
    }
    private boolean safe(LandWarp warp) {
        World world = Bukkit.getWorld(warp.worldId());
        if (world == null || warp.y() < world.getMinHeight() + 1 || warp.y() + 1 >= world.getMaxHeight()
                || !loadExistingChunk(world, warp.chunk().x(), warp.chunk().z())) return false;
        if (!world.getWorldBorder().isInside(new Location(world, warp.x(), warp.y(), warp.z()))) return false;
        // Test actual block collision shapes, not whole occupied blocks. Indoor
        // ceilings, carpets, slabs and stair landings need sub-block precision.
        var body = new org.bukkit.util.BoundingBox(warp.x() - .299999, warp.y() + 1E-7, warp.z() - .299999,
                warp.x() + .299999, warp.y() + 1.8 - 1E-7, warp.z() + .299999);
        var feet = new org.bukkit.util.BoundingBox(body.getMinX(), warp.y() - .06, body.getMinZ(),
                body.getMaxX(), warp.y() + 1E-7, body.getMaxZ());
        boolean supported = false;
        for (int x = (int)Math.floor(warp.x() - .3); x <= (int)Math.floor(warp.x() + .3); x++) {
            for (int z = (int)Math.floor(warp.z() - .3); z <= (int)Math.floor(warp.z() + .3); z++) {
                if (!loadExistingChunk(world, x >> 4, z >> 4)) return false;
                for (int y = Math.max(world.getMinHeight(), (int)Math.floor(warp.y()) - 2); y <= (int)Math.floor(body.getMaxY()); y++) {
                    var block = world.getBlockAt(x, y, z);
                    var cube = new org.bukkit.util.BoundingBox(x, y, z, x + 1, y + 1, z + 1);
                    if ((body.overlaps(cube) || feet.overlaps(cube)) && (block.isLiquid() || dangerous(block.getType()))) return false;
                    for (var shape : block.getCollisionShape().getBoundingBoxes()) {
                        shape.shift(x, y, z);
                        if (shape.overlaps(body)) return false;
                        if (shape.overlaps(feet)) supported = true;
                    }
                }
            }
        }
        return supported;
    }
    private boolean loadExistingChunk(World world, int x, int z) {
        return world.isChunkLoaded(x, z) || world.isChunkGenerated(x, z) && world.loadChunk(x, z, false);
    }
    private static boolean dangerous(Material type) {
        return switch (type) { case FIRE, SOUL_FIRE, LAVA, MAGMA_BLOCK, CAMPFIRE, SOUL_CAMPFIRE, CACTUS,
                SWEET_BERRY_BUSH, POWDER_SNOW, WITHER_ROSE -> true; default -> false; };
    }
    private void cancel(Player player) { if (pending.remove(player.getUniqueId()) != null) tell(player, "warps.cancelled"); }
    private void tell(Player player, String key) { player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player), key)); }
    @EventHandler public void quit(PlayerQuitEvent event) { pending.remove(event.getPlayer().getUniqueId()); }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void damage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && event.getFinalDamage() > 0) cancel(player);
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void move(PlayerMoveEvent event) {
        if (event.getTo() != null && moved(event.getTo(), event.getFrom())) cancel(event.getPlayer());
    }
    public static int limit(Player player, int fallback) {
        var nodes = new HashSet<String>();
        for (var permission : player.getEffectivePermissions()) {
            if (!permission.getValue()) continue;
            nodes.add(permission.getPermission());
        }
        return WarpLimits.resolve(nodes, fallback);
    }
}
