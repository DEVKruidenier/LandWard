package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.*;
import io.github.devkruidenier.landward.platform.TaskRegistration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.EntityDamageEvent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Warmups follow their player across regions; destination reads never block another region. */
public final class WarpTeleportService implements Listener {
    private final LandWardPlugin plugin;
    private final LandManager lands;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private static final class Pending {
        final UUID land; final LandWarp warp; final Location origin; final boolean admin;
        int remaining; TaskRegistration task;
        Pending(UUID land, LandWarp warp, Location origin, boolean admin, int ticks) {
            this.land=land; this.warp=warp; this.origin=origin; this.admin=admin; this.remaining=ticks;
        }
        void stop() { if (task != null) task.cancel(); }
    }
    public WarpTeleportService(LandWardPlugin plugin, LandManager lands) { this.plugin=plugin; this.lands=lands; }
    public void shutdown() { pending.values().forEach(Pending::stop); pending.clear(); }
    public void begin(Player player, UUID land, UUID warp, boolean admin) {
        var selected = lands.features(land).warps().stream().filter(value -> value.id().equals(warp)).findFirst().orElse(null);
        if (selected == null || !permitted(player, land, selected, admin)) { tell(player, "warps.unavailable"); return; }
        Pending request=new Pending(land, selected, player.getLocation().clone(), admin,
                Math.max(0, plugin.getConfig().getInt(admin ? "teleportation.admin-warp-delay-seconds" : "teleportation.warp-delay-seconds", 2)) * 20);
        Pending old=pending.put(player.getUniqueId(), request); if(old!=null) old.stop();
        tell(player, "warps.warming");
        if(request.remaining==0) finish(player,request);
        else request.task=plugin.scheduler().playerRepeat(player, () -> advance(player,request),1);
    }
    private boolean permitted(Player player, UUID land, LandWarp warp, boolean admin) {
        World world=Bukkit.getWorld(warp.worldId());
        return player.isOnline() && plugin.isLandWorld(player.getWorld()) && world!=null && plugin.isLandWorld(world)
                && !plugin.combatTags().isTagged(player)
                && (admin ? player.hasPermission("landward.admin") : lands.canUseWarps(player.getUniqueId(), land))
                && lands.landAt(warp.chunk()).map(value -> value.id().equals(land)).orElse(false)
                && lands.features(land).warps().contains(warp);
    }
    private boolean current(Player player, Pending request) {
        return pending.get(player.getUniqueId())==request && !moved(player.getLocation(),request.origin)
                && permitted(player,request.land,request.warp,request.admin);
    }
    private void advance(Player player, Pending request) {
        if(pending.get(player.getUniqueId())!=request) {request.stop();return;}
        if(!current(player,request)) {cancel(player);return;}
        if(--request.remaining<=0) finish(player,request);
    }
    private void finish(Player player, Pending request) {
        request.stop();
        WarpSafety.check(plugin.scheduler(),Bukkit.getWorld(request.warp.worldId()),request.warp)
                .whenComplete((safe,error) -> plugin.scheduler().player(player, () -> {
                    if(pending.get(player.getUniqueId())!=request) return;
                    if(error!=null || !Boolean.TRUE.equals(safe) || !current(player,request)) {
                        pending.remove(player.getUniqueId(),request); tell(player,"warps.unavailable"); return;
                    }
                    pending.remove(player.getUniqueId(),request);
                    LandWarp warp=request.warp;
                    plugin.scheduler().teleport(player,new Location(Bukkit.getWorld(warp.worldId()),warp.x(),warp.y(),warp.z(),warp.yaw(),warp.pitch()))
                            .whenComplete((ok,failure) -> plugin.scheduler().player(player,
                                    () -> tell(player,failure==null && Boolean.TRUE.equals(ok) ? "warps.complete" : "warps.unavailable")));
                }));
    }
    static boolean moved(Location current, Location origin) {
        return !Objects.equals(current.getWorld(), origin.getWorld()) || current.getX() != origin.getX()
                || current.getY() != origin.getY() || current.getZ() != origin.getZ();
    }
    private void cancel(Player player) {
        Pending old=pending.remove(player.getUniqueId());
        if(old!=null) {old.stop();tell(player,"warps.cancelled");}
    }
    private void tell(Player player,String key) {player.sendMessage(plugin.localeService().message(plugin.localeService().localeFor(player),key));}
    @EventHandler public void quit(PlayerQuitEvent event) {
        Pending old=pending.remove(event.getPlayer().getUniqueId());if(old!=null)old.stop();
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.MONITOR) public void damage(EntityDamageEvent event) {
        if(event.getEntity() instanceof Player player && event.getFinalDamage()>0)cancel(player);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.MONITOR) public void move(PlayerMoveEvent event) {
        if(event.getTo()!=null && moved(event.getTo(),event.getFrom()))cancel(event.getPlayer());
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
