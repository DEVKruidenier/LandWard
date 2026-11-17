package io.github.devkruidenier.landward.fixture;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.*;
import io.github.devkruidenier.landward.protection.ClaimProtectionListener;
import io.github.devkruidenier.landward.storage.sqlite.SqliteLandStorage;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Proxy;
import java.util.*;

/** Console-only event probes against a real server API using isolated claim data. */
public final class FeatureServerProbe extends JavaPlugin {
    private int assertions;
    @Override public void onEnable() {
        getCommand("featureprobe").setExecutor((sender, command, label, args) -> {
            try { run(); getLogger().info("FEATURE-PROBE-PASS assertions=" + assertions); }
            catch (Throwable error) { getLogger().log(java.util.logging.Level.SEVERE, "FEATURE-PROBE-FAIL", error); }
            return true;
        });
    }
    private void run() throws Exception {
        assertions = 0;
        var plugin = (LandWardPlugin)Bukkit.getPluginManager().getPlugin("LandWard");
        var world = Bukkit.getWorlds().getFirst();
        java.nio.file.Files.createDirectories(getDataFolder().toPath());
        var storage = new SqliteLandStorage(getDataFolder().toPath().resolve(UUID.randomUUID() + ".db")); storage.initialize();
        var lands = new LandManager(storage); lands.load();
        UUID owner = UUID.randomUUID(), member = UUID.randomUUID(), visitor = UUID.randomUUID();
        var first = new ChunkPosition(world.getUID(), 1000, 1000);
        var safe = new ChunkPosition(world.getUID(), 1010, 1000);
        var land = lands.create(owner, "Probe Player", first).land(); lands.addMember(owner, member);
        var admin = lands.claimAdministration("Probe Safe", safe).land();
        var listener = new ClaimProtectionListener(plugin, lands, plugin.combatTags(), plugin.adminBypasses());
        var movement = io.github.devkruidenier.landward.gui.WarpTeleportService.class.getDeclaredMethod("moved", Location.class, Location.class);
        movement.setAccessible(true);
        var original = new Location(world, 10.25, 80, 12.75, 0, 0);
        var rotated = original.clone(); rotated.setYaw(90); rotated.setPitch(40);
        check(!(boolean)movement.invoke(null, original, rotated), "Head rotation does not cancel a warmup");
        check((boolean)movement.invoke(null, original, original.clone().add(.01, 0, 0)), "Position movement cancels a warmup");
        EntityDamageEvent.DamageCause[] causes = {EntityDamageEvent.DamageCause.FALL, EntityDamageEvent.DamageCause.FIRE,
                EntityDamageEvent.DamageCause.FIRE_TICK, EntityDamageEvent.DamageCause.LAVA,
                EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION};
        EnvironmentSettings.Rule[] damageRules = {EnvironmentSettings.Rule.FALL_DAMAGE, EnvironmentSettings.Rule.FIRE_PLAYER_DAMAGE,
                EnvironmentSettings.Rule.FIRE_PLAYER_DAMAGE, EnvironmentSettings.Rule.LAVA_PLAYER_DAMAGE,
                EnvironmentSettings.Rule.EXPLOSION_PLAYER_DAMAGE, EnvironmentSettings.Rule.EXPLOSION_PLAYER_DAMAGE};
        for (Land selected : List.of(land, admin)) {
            ChunkPosition chunk = selected == land ? first : safe;
            Block target = world.getBlockAt(chunk.x() * 16 + 2, 80, chunk.z() * 16 + 2);
            for (boolean allowed : new boolean[]{false, true}) {
                var rules = allowed ? EnumSet.allOf(EnvironmentSettings.Rule.class) : EnumSet.noneOf(EnvironmentSettings.Rule.class);
                lands.environment(selected.id(), new EnvironmentSettings(rules));
                for (UUID identity : List.of(owner, member, visitor)) {
                    Player player = proxy(Player.class, identity, target.getLocation(), EntityType.PLAYER);
                    for (int i = 0; i < causes.length; i++) {
                        var event = new EntityDamageEvent(player, causes[i], 5);
                        listener.onEnvironmentalDamage(event); check(event.isCancelled() == !allowed, selected.name() + " " + damageRules[i]);
                        event.setCancelled(true); listener.onEnvironmentalDamage(event); check(event.isCancelled(), "Existing cancellation preserved");
                    }
                }
                var ignite = new BlockIgniteEvent(target, BlockIgniteEvent.IgniteCause.LAVA, (Entity)null, null);
                listener.onBlockIgnite(ignite); check(ignite.isCancelled() == !allowed, "Lava ignition");
                var lightning = new BlockIgniteEvent(target, BlockIgniteEvent.IgniteCause.LIGHTNING, (Entity)null, null);
                listener.onBlockIgnite(lightning); check(lightning.isCancelled() == !allowed, "Lightning ignition");
                var burn = new BlockBurnEvent(target); listener.onBlockBurn(burn); check(burn.isCancelled() == !allowed, "Unknown fire source");
                Block internalFire = target.getRelative(1, 0, 0); var previousFireBlock = internalFire.getBlockData();
                try {
                    internalFire.setType(Material.FIRE, false);
                    var internalSpread = new BlockSpreadEvent(target, internalFire, target.getState());
                    listener.onBlockSpread(internalSpread); check(internalSpread.isCancelled() == !allowed, "Fire spread within the same claim");
                } finally { internalFire.setBlockData(previousFireBlock, false); }
                var mob = proxy(Mob.class, UUID.randomUUID(), target.getLocation(), EntityType.ENDERMAN);
                var change = new EntityChangeBlockEvent(mob, target, Material.AIR.createBlockData());
                listener.onEntityChangeBlock(change); check(change.isCancelled() == !allowed, "Mob block changes");
                var falling = new EntityChangeBlockEvent(proxy(Entity.class, UUID.randomUUID(), target.getLocation(), EntityType.FALLING_BLOCK),
                        target, Material.SAND.createBlockData());
                listener.onEntityChangeBlock(falling); check(falling.isCancelled(), "Non-mob block placement retains its existing protection");
                for (EntityType type : new EntityType[]{EntityType.TNT, EntityType.TNT_MINECART, EntityType.CREEPER, EntityType.GHAST, EntityType.WITHER}) {
                    Entity entity = proxy(Entity.class, UUID.randomUUID(), target.getLocation(), type);
                    var event = new EntityExplodeEvent(entity, target.getLocation(), new ArrayList<>(List.of(target)), 1, ExplosionResult.DESTROY);
                    listener.onEntityExplode(event); check(event.blockList().contains(target) == allowed, type.name());
                }
                var event = new BlockExplodeEvent(target, target.getState(), new ArrayList<>(List.of(target)), 1, ExplosionResult.DESTROY);
                listener.onBlockExplode(event); check(event.blockList().contains(target) == allowed, "Beds/anchors and block explosions");
                // Separate TNT and non-TNT toggles cannot affect one another.
                lands.environment(selected.id(), new EnvironmentSettings(EnumSet.of(EnvironmentSettings.Rule.TNT_BLOCK_DAMAGE)));
                for (EntityType type : new EntityType[]{EntityType.TNT, EntityType.CREEPER}) {
                    var split = new EntityExplodeEvent(proxy(Entity.class, UUID.randomUUID(), target.getLocation(), type), target.getLocation(),
                            new ArrayList<>(List.of(target)), 1, ExplosionResult.DESTROY);
                    listener.onEntityExplode(split); check(split.blockList().contains(target) == (type == EntityType.TNT), "Independent explosion switches");
                }
            }
            lands.environment(selected.id(), new EnvironmentSettings(EnumSet.allOf(EnvironmentSettings.Rule.class)));
            Block source = world.getBlockAt(chunk.x() * 16 + 3, 80, chunk.z() * 16 + 2);
            Material previous = source.getType(); source.setType(Material.WATER, false);
            var flow = new BlockFromToEvent(source, target); listener.onFluidFlow(flow); check(!flow.isCancelled(), "Internal flow allowed");
            lands.environment(selected.id(), new EnvironmentSettings(Set.of()));
            flow = new BlockFromToEvent(source, target); listener.onFluidFlow(flow); check(flow.isCancelled(), "Internal water denied");
            source.setType(Material.LAVA, false);
            flow = new BlockFromToEvent(source, target); listener.onFluidFlow(flow); check(flow.isCancelled(), "Internal lava denied");
            source.setType(Material.DRAGON_EGG, false);
            flow = new BlockFromToEvent(source, target); listener.onFluidFlow(flow); check(!flow.isCancelled(), "Water/lava switches do not control dragon eggs");
            lands.environment(selected.id(), new EnvironmentSettings(EnumSet.allOf(EnvironmentSettings.Rule.class)));
            Block outside = world.getBlockAt((chunk.x() + 1) * 16, 80, chunk.z() * 16 + 2);
            flow = new BlockFromToEvent(source, outside); listener.onFluidFlow(flow); check(flow.isCancelled(), "Outward flow always denied");
            flow = new BlockFromToEvent(outside, target); listener.onFluidFlow(flow); check(flow.isCancelled(), "Inward flow always denied");
            var spread = new BlockSpreadEvent(target, outside, target.getState()); listener.onBlockSpread(spread); check(spread.isCancelled(), "Spread across boundary denied");
            source.setType(previous, false);
        }
        lands.environment(land.id(), new EnvironmentSettings(Set.of()));
        lands.environment(admin.id(), new EnvironmentSettings(EnumSet.allOf(EnvironmentSettings.Rule.class)));
        Block protectedBlock = world.getBlockAt(first.x() * 16 + 2, 80, first.z() * 16 + 2);
        Block allowedBlock = world.getBlockAt(safe.x() * 16 + 2, 80, safe.z() * 16 + 2);
        Block wilderness = world.getBlockAt(2000 * 16, 80, 2000 * 16);
        var mixed = new EntityExplodeEvent(proxy(Entity.class, UUID.randomUUID(), wilderness.getLocation(), EntityType.TNT),
                wilderness.getLocation(), new ArrayList<>(List.of(protectedBlock, allowedBlock, wilderness)), 1, ExplosionResult.DESTROY);
        listener.onEntityExplode(mixed);
        check(!mixed.blockList().contains(protectedBlock), "Wilderness explosion protects denied neighboring land");
        check(mixed.blockList().contains(allowedBlock), "Allowed land cannot grant explosion rights to another land");
        check(mixed.blockList().contains(wilderness), "Wilderness stays unaffected");
        List<String> disabled = plugin.getConfig().getStringList("claims.disabled-worlds");
        var previous = new ArrayList<>(disabled); disabled.add(world.getName()); plugin.getConfig().set("claims.disabled-worlds", disabled);
        try {
            lands.environment(land.id(), new EnvironmentSettings(Set.of()));
            Player player = proxy(Player.class, owner, new Location(world, 16002, 80, 16002), EntityType.PLAYER);
            var event = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.FALL, 5);
            listener.onEnvironmentalDamage(event); check(!event.isCancelled(), "Disabled world is inactive");
        } finally { plugin.getConfig().set("claims.disabled-worlds", previous); }
        storage.close();
    }
    private void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, UUID id, Location location, EntityType entityType) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, arguments) -> {
            return switch (method.getName()) {
                case "getUniqueId" -> id; case "getLocation" -> location.clone(); case "getWorld" -> location.getWorld();
                case "getType" -> entityType; case "getName" -> "Probe"; case "isOnline" -> true;
                case "hashCode" -> id.hashCode(); case "equals" -> proxy == arguments[0]; case "toString" -> "FeatureProbe " + id;
                default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0
                        : method.getReturnType() == double.class ? 0D : method.getReturnType() == float.class ? 0F : null;
            };
        });
    }
}
