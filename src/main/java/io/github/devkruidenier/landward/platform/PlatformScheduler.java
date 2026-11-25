package io.github.devkruidenier.landward.platform;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Folia ownership boundaries, with no Paper-only class loaded on Spigot. */
public final class PlatformScheduler implements AutoCloseable {
    private final Plugin plugin;
    private final boolean folia;
    private final Set<TaskRegistration> tasks = ConcurrentHashMap.newKeySet();
    private final Set<CompletableFuture<?>> futures = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public PlatformScheduler(Plugin plugin) {
        this.plugin = plugin;
        boolean detected;
        try { Class.forName("io.papermc.paper.threadedregions.RegionizedServer"); detected = true; }
        catch (ClassNotFoundException ignored) { detected = false; }
        folia = detected;
    }
    public boolean folia() { return folia; }
    public boolean ownsBlock(org.bukkit.block.Block block) { return !folia ? Bukkit.isPrimaryThread() : Folia.ownsBlock(block); }
    public boolean owns(Player player) { return !folia ? Bukkit.isPrimaryThread() : Folia.owns(player); }
    public void sender(CommandSender sender, Runnable action) {
        if (sender instanceof Player player) player(player, action); else global(action);
    }
    public TaskRegistration global(Runnable action) { return globalLater(action, 1); }
    public TaskRegistration globalLater(Runnable action, long delay) { return schedule(null, action, delay, 0); }
    public TaskRegistration globalRepeat(Runnable action, long delay, long period) { return schedule(null, action, delay, period); }
    public TaskRegistration player(Player player, Runnable action) { return playerLater(player, action, 1); }
    public TaskRegistration playerLater(Player player, Runnable action, long delay) { return schedule(player, action, delay, 0); }
    public TaskRegistration playerRepeat(Player player, Runnable action, long period) { return schedule(player, action, 1, period); }

    private TaskRegistration schedule(Player player, Runnable action, long delay, long period) {
        TaskRegistration registration = new TaskRegistration();
        tasks.add(registration);
        if (closed) { registration.cancel(); tasks.remove(registration); return registration; }
        Runnable run = () -> {
            try { if (!closed && !registration.cancelled() && (player == null || player.isOnline())) action.run(); }
            finally { if (period == 0) tasks.remove(registration); }
        };
        try {
            Runnable cancel;
            if (folia) cancel = Folia.schedule(plugin, player, run, () -> { registration.cancel(); tasks.remove(registration); }, Math.max(1, delay), period);
            else {
                var task = period > 0 ? Bukkit.getScheduler().runTaskTimer(plugin, run, Math.max(1, delay), period)
                        : Bukkit.getScheduler().runTaskLater(plugin, run, Math.max(1, delay));
                cancel = task::cancel;
            }
            registration.bind(() -> { cancel.run(); tasks.remove(registration); });
        } catch (RuntimeException error) { tasks.remove(registration); throw error; }
        return registration;
    }

    /** Runs only after loading the existing chunk, on its owning region. Never blocks a region. */
    public <T> CompletableFuture<T> atChunk(World world, int x, int z, Supplier<T> action) {
        CompletableFuture<T> result = track(new CompletableFuture<>());
        if (closed) return result;
        Runnable run = () -> {
            if (closed || result.isDone()) return;
            try { result.complete(action.get()); } catch (Throwable error) { result.completeExceptionally(error); }
        };
        if (!folia) {
            global(() -> {
                if (!world.isChunkLoaded(x, z) && !(world.isChunkGenerated(x, z) && world.loadChunk(x, z, false)))
                    result.completeExceptionally(new IllegalStateException("Destination chunk does not exist"));
                else run.run();
            });
        } else {
            try { Folia.atChunk(plugin, world, x, z, run, result); }
            catch (RuntimeException error) { result.completeExceptionally(error); }
        }
        return result;
    }

    /** Caller is on the entity's owner; the completion is delivered there too. */
    public CompletableFuture<Boolean> teleport(Player player, Location destination) {
        if (!owns(player)) throw new IllegalStateException("Teleport must start on the player's owner");
        if (closed) return CompletableFuture.completedFuture(false);
        return folia ? track(Folia.teleport(player, destination)) : CompletableFuture.completedFuture(player.teleport(destination));
    }

    public void surfaceTeleport(Player player, World world, int x, int z, java.util.function.BooleanSupplier authorized,
                                java.util.function.Consumer<Boolean> completed) {
        atChunk(world, x >> 4, z >> 4, () -> new Location(world, x + .5, world.getHighestBlockYAt(x, z) + 1, z + .5))
                .whenComplete((destination, error) -> player(player, () -> {
                    if (error != null || !authorized.getAsBoolean()) { completed.accept(false); return; }
                    teleport(player, destination).whenComplete((ok, failure) -> player(player,
                            () -> completed.accept(failure == null && Boolean.TRUE.equals(ok))));
                }));
    }
    private <T> CompletableFuture<T> track(CompletableFuture<T> future) {
        futures.add(future);
        future.whenComplete((value, error) -> futures.remove(future));
        if (closed) future.cancel(false);
        return future;
    }
    @Override public void close() {
        closed = true;
        tasks.forEach(TaskRegistration::cancel); tasks.clear();
        futures.forEach(future -> future.cancel(false)); futures.clear();
        if (folia) Folia.cancel(plugin);
    }

    /** Loaded only on Folia: preserves the Spigot classpath contract. */
    private static final class Folia {
        static boolean ownsBlock(org.bukkit.block.Block block) { return Bukkit.isOwnedByCurrentRegion(block.getLocation()); }
        static boolean owns(Player player) { return Bukkit.isOwnedByCurrentRegion(player); }
        static Runnable schedule(Plugin plugin, Player player, Runnable action, Runnable retired, long delay, long period) {
            io.papermc.paper.threadedregions.scheduler.ScheduledTask task;
            if (player != null) task = period > 0
                    ? player.getScheduler().runAtFixedRate(plugin, ignored -> action.run(), retired, delay, period)
                    : player.getScheduler().runDelayed(plugin, ignored -> action.run(), retired, delay);
            else task = period > 0
                    ? Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, ignored -> action.run(), delay, period)
                    : Bukkit.getGlobalRegionScheduler().runDelayed(plugin, ignored -> action.run(), delay);
            if (task == null) { retired.run(); return () -> {}; }
            return task::cancel;
        }
        static void atChunk(Plugin plugin, World world, int x, int z, Runnable action, CompletableFuture<?> result) {
            world.getChunkAtAsync(x, z, false).whenComplete((chunk, error) -> {
                if (result.isDone()) return;
                if (error != null) result.completeExceptionally(error);
                else if (chunk == null) result.completeExceptionally(new IllegalStateException("Destination chunk does not exist"));
                else {
                    try { Bukkit.getRegionScheduler().execute(plugin, world, x, z, action); }
                    catch (RuntimeException failure) { result.completeExceptionally(failure); }
                }
            });
        }
        static CompletableFuture<Boolean> teleport(Player player, Location destination) { return player.teleportAsync(destination); }
        static void cancel(Plugin plugin) { Bukkit.getGlobalRegionScheduler().cancelTasks(plugin); }
    }
}
