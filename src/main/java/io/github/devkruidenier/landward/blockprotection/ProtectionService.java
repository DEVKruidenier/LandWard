package io.github.devkruidenier.landward.blockprotection;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Serialized durable mutations off region threads; committed records are cached by LandManager. */
public final class ProtectionService implements AutoCloseable {
  private final LandWardPlugin plugin;
  private final LandManager lands;
  private final ThreadPoolExecutor writer;
  private final Map<BlockKey, UUID> uncertain = new ConcurrentHashMap<>();
  private final Object markers = new Object();
  private volatile boolean closed;

  public ProtectionService(LandWardPlugin plugin, LandManager lands) {
    this.plugin = plugin;
    this.lands = lands;
    writer =
        new ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(4096),
            r -> {
              var t = new Thread(r, "LandWard-block-protection");
              t.setDaemon(true);
              return t;
            },
            new ThreadPoolExecutor.AbortPolicy());
  }

  public boolean enabled() {
    return !closed && plugin.getConfig().getBoolean("block-protection.enabled", false);
  }

  public boolean uncertain(BlockKey b) {
    return uncertain.containsKey(b);
  }

  public ProtectionActor actor(Player p) {
    return new ProtectionActor(
        p.getUniqueId(),
        p.hasPermission("landward.protection.create"),
        p.hasPermission("landward.protection.own"),
        p.hasPermission("landward.protection.manage"),
        p.hasPermission("landward.admin.protection.manage"));
  }

  public boolean visible(Player p, UUID land) {
    var a = actor(p);
    return enabled()
        && (lands.personalMember(land, a.id()) && (a.create() || a.own() || a.landManage())
            || a.adminManage()
                && lands.land(land).filter(l -> !lands.isAdministrative(l)).isPresent());
  }

  public boolean denied(Player p, BlockKey b) {
    if (!enabled()) return false;
    if (uncertain(b)) return true;
    var lock = lands.personalProtections().at(b);
    return lock != null
        && !lands.personalAccess(
            p.getUniqueId(),
            lock,
            plugin.getConfig().getBoolean("block-protection.land-owner-bypass", false),
            p.hasPermission("landward.admin.protection.bypass"));
  }

  public boolean mechanical(BlockKey b) {
    return enabled() && (uncertain(b) || lands.personalProtections().at(b) != null);
  }

  public void tell(Player p, String key) {
    p.sendMessage(
        plugin
            .localeService()
            .message(plugin.localeService().localeFor(p), "block-protection." + key));
  }

  public void tell(Player p, String key, Map<String, ?> data) {
    p.sendMessage(
        plugin
            .localeService()
            .message(plugin.localeService().localeFor(p), "block-protection." + key, data));
  }

  public record Selection(
      UUID land,
      String material,
      List<BlockKey> blocks,
      Map<BlockKey, UUID> generations,
      String shape) {
    public Selection {
      blocks = List.copyOf(blocks);
      generations = Map.copyOf(generations);
    }
  }

  public CompletableFuture<Selection> inspect(UUID land, BlockKey key) {
    var world = Bukkit.getWorld(key.world());
    if (world == null)
      return CompletableFuture.failedFuture(new IllegalArgumentException("changed"));
    return plugin
        .scheduler()
        .atChunk(
            world,
            key.x() >> 4,
            key.z() >> 4,
            () -> ProtectionBlocks.part(world.getBlockAt(key.x(), key.y(), key.z())))
        .thenCompose(first -> {
          var tasks =
              first.unit().stream()
                  .map(b ->
                      plugin
                          .scheduler()
                          .atChunk(
                              world,
                              b.x() >> 4,
                              b.z() >> 4,
                              () -> ProtectionBlocks.part(world.getBlockAt(b.x(), b.y(), b.z()))))
                  .toList();
          return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new))
              .thenApply(v -> {
                var generations = new HashMap<BlockKey, UUID>();
                for (var task : tasks) {
                  var part = task.join();
                  if (!part.material().equals(first.material())
                      || !Set.copyOf(part.unit()).equals(Set.copyOf(first.unit()))
                      || !part.shape().equals(first.shape())
                      || !lands
                          .landAt(part.key().chunk())
                          .map(l -> l.id().equals(land))
                          .orElse(false)
                      || uncertain(part.key())) throw new IllegalArgumentException("changed");
                  var placed = lands.personalProtections().placement(part.key());
                  if (placed != null) generations.put(part.key(), placed.generation());
                }
                return new Selection(
                    land, first.material(), first.unit(), generations, first.shape());
              });
        });
  }

  public void submit(Player p, Checked action, Runnable success) {
    submit(p, action, success, () -> {});
  }

  private void submit(Player p, Checked action, Runnable success, Runnable cleanup) {
    if (!enabled()) {
      cleanup.run();
      tell(p, "disabled");
      return;
    }
    try {
      writer.execute(() -> {
        try {
          if (!enabled()) return;
          action.run();
          plugin
              .scheduler()
              .player(
                  p,
                  () -> {
                    if (enabled()) success.run();
                  });
        } catch (Exception e) {
          plugin
              .getLogger()
              .log(
                  java.util.logging.Level.WARNING,
                  "Personal block protection mutation failed for " + p.getUniqueId(),
                  e);
          plugin
              .scheduler()
              .player(
                  p,
                  () ->
                      tell(
                          p,
                          e instanceof IllegalArgumentException
                                  && Set.of(
                                          "denied",
                                          "changed",
                                          "unknown-owner",
                                          "limit",
                                          "unsupported")
                                      .contains(e.getMessage())
                              ? e.getMessage()
                              : "storage-error"));
        } finally {
          cleanup.run();
        }
      });
    } catch (RejectedExecutionException e) {
      cleanup.run();
      tell(p, "storage-error");
    }
  }

  @FunctionalInterface
  public interface Checked {
    void run() throws Exception;
  }

  /** Pending/failed persistence never turns a known lock into an unprotected replacement. */
  public void observe(List<BlockPlacement> placements, List<BlockKey> removed, Runnable committed) {
    var keys = new HashSet<>(removed);
    placements.forEach(p -> keys.add(p.position()));
    UUID marker = UUID.randomUUID();
    synchronized (markers) {
      keys.forEach(k -> uncertain.put(k, marker));
    }
    try {
      writer.execute(() -> {
        try {
          if (!removed.isEmpty()) lands.forgetPersonal(removed);
          for (var p : placements) lands.recordPlacement(p);
          keys.forEach(k -> uncertain.remove(k, marker));
          if (committed != null) committed.run();
        } catch (Exception e) {
          plugin
              .getLogger()
              .log(
                  java.util.logging.Level.SEVERE,
                  "Cannot persist block ownership at "
                      + keys
                      + "; locations remain quarantined until restart/recovery",
                  e);
        }
      });
    } catch (RejectedExecutionException e) {
      plugin
          .getLogger()
          .severe("Personal protection writer is full; locations quarantined: " + keys);
    }
  }

  public void create(Player p, Selection selected, Consumer<BlockProtection> success) {
    inspect(selected.land(), selected.blocks().getFirst())
        .whenComplete((now, error) ->
            plugin
                .scheduler()
                .player(
                    p,
                    () -> {
                      if (error != null
                          || !selected.equals(now)
                          || !p.getWorld().getUID().equals(selected.blocks().getFirst().world())) {
                        tell(p, "changed");
                        return;
                      }
                      var currentActor = actor(p);
                      UUID marker = UUID.randomUUID();
                      synchronized (markers) {
                        if (selected.blocks().stream().anyMatch(uncertain::containsKey)) {
                          tell(p, "changed");
                          return;
                        }
                        selected.blocks().forEach(b -> uncertain.put(b, marker));
                      }
                      submit(
                          p,
                          () -> {
                            if (!enabled()
                                || selected.blocks().stream()
                                    .anyMatch(b -> !marker.equals(uncertain.get(b))))
                              throw new IllegalArgumentException("changed");
                            var saved =
                                lands.protect(
                                    currentActor,
                                    selected.land(),
                                    selected.material(),
                                    selected.blocks(),
                                    selected.generations(),
                                    plugin
                                        .getConfig()
                                        .getInt("block-protection.max-blocks-per-player", -1));
                            for (var key : saved.blocks()) {
                              var world = Bukkit.getWorld(key.world());
                              if (world != null)
                                plugin
                                    .scheduler()
                                    .atChunk(
                                        world,
                                        key.x() >> 4,
                                        key.z() >> 4,
                                        () -> {
                                          if (!enabled()
                                              || lands.personalProtections().get(saved.id())
                                                  == null) return false;
                                          var block = world.getBlockAt(key.x(), key.y(), key.z());
                                          var data = block.getBlockData();
                                          if (data instanceof org.bukkit.block.data.Openable open) {
                                            open.setOpen(false);
                                            if (data
                                                instanceof org.bukkit.block.data.Powerable power)
                                              power.setPowered(false);
                                            block.setBlockData(data, false);
                                          }
                                          return true;
                                        });
                            }
                            plugin
                                .scheduler()
                                .player(
                                    p,
                                    () -> {
                                      if (enabled()) success.accept(saved);
                                    });
                          },
                          () -> tell(p, "created"),
                          () -> selected.blocks().forEach(b -> uncertain.remove(b, marker)));
                    }));
  }

  @Override
  public void close() {
    closed = true;
    writer.shutdown();
    try {
      if (!writer.awaitTermination(10, TimeUnit.SECONDS))
        plugin.getLogger().severe("Block protection writer did not drain before shutdown");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
