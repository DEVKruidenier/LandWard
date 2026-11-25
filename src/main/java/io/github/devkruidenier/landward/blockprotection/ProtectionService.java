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
  private volatile AutomationOwnership.Snapshot automation =
      new AutomationOwnership.Snapshot(-1, Map.of());
  private final java.util.concurrent.atomic.AtomicBoolean automationQueued =
      new java.util.concurrent.atomic.AtomicBoolean();
  private ProtectionQuarantine quarantine;
  private volatile boolean quarantineUnavailable;
  private volatile boolean storageUnavailable;

  public void storageUnavailable() {
    storageUnavailable = true;
  }

  public ProtectionService(LandWardPlugin plugin, LandManager lands) {
    this.plugin = plugin;
    this.lands = lands;
    lands.personalCleanup(this::lifecycleCleanup);
    try {
      quarantine =
          new ProtectionQuarantine(
              plugin.getDataFolder().toPath().resolve("block-protection-quarantine.txt"));
      quarantine.locations().forEach(b -> uncertain.put(b, UUID.randomUUID()));
    } catch (java.io.IOException error) {
      quarantineUnavailable = true;
      plugin
          .getLogger()
          .log(
              java.util.logging.Level.SEVERE,
              "Cannot verify block-protection-quarantine.txt; personal block interactions in player"
                  + " lands are denied until repaired",
              error);
    }
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
    if (storageUnavailable) {
      var world = Bukkit.getWorld(b.world());
      return world != null && plugin.isLandWorld(world);
    }
    return uncertain.containsKey(b)
        || quarantineUnavailable
            && lands.landAt(b.chunk()).filter(l -> !lands.isAdministrative(l)).isPresent();
  }

  public record AutomationState(UUID owner, boolean uncertain) {}

  public AutomationState automation(BlockKey key) {
    var placement = lands.personalProtections().placement(key);
    if (placement == null || !placement.material().equals("HOPPER"))
      return new AutomationState(null, false);
    long revision = lands.personalProtections().revision();
    var snapshot = automation;
    if (snapshot.revision() != revision) {
      if (automationQueued.compareAndSet(false, true)) {
        try {
          writer.execute(() -> {
            try {
              long version = lands.personalProtections().revision();
              var updated =
                  AutomationOwnership.build(
                      version,
                      lands.personalProtections().placements(),
                      lands.personalProtections().all());
              if (version == lands.personalProtections().revision()) automation = updated;
            } finally {
              automationQueued.set(false);
            }
          });
        } catch (RejectedExecutionException full) {
          automationQueued.set(false);
        }
      }
      return new AutomationState(null, true);
    }
    return new AutomationState(snapshot.privateHoppers().get(key), false);
  }

  public Set<UUID> automationOrigins(BlockKey key) {
    return automation.origins().getOrDefault(key, Set.of());
  }

  public boolean automationRights(UUID owner, BlockKey key) {
    var land = lands.landAt(key.chunk()).orElse(null);
    if (land == null) return true;
    if (lands.isAdministrative(land))
      return lands.permissions(owner, land.id()).containers()
          || plugin.getConfig().getBoolean("admin-claims.protection.allow-containers", false);
    return lands.personalMember(land.id(), owner)
        && lands.permissions(owner, land.id()).containers();
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
        && !storageUnavailable
        && (lands.personalMember(land, a.id()) && (a.create() || a.own() || a.landManage())
            || a.adminManage()
                && lands.land(land).filter(l -> !lands.isAdministrative(l)).isPresent());
  }

  public boolean denied(Player p, BlockKey b) {
    if (!enabled()) return false;
    if (uncertain(b)) return true;
    var lock = lands.personalProtections().at(b);
    if (lock != null)
      return !lands.personalAccess(
          p.getUniqueId(),
          lock,
          plugin.getConfig().getBoolean("block-protection.land-owner-bypass", false),
          p.hasPermission("landward.admin.protection.bypass"));
    var state = automation(b);
    if (state.uncertain()) return true;
    if (state.owner() == null) return false;
    var origins = automation.origins().getOrDefault(b, Set.of());
    if (origins.isEmpty()) return true;
    for (var id : origins) {
      var source = lands.personalProtections().get(id);
      if (source == null
          || !lands.personalAccess(
              p.getUniqueId(),
              source,
              plugin.getConfig().getBoolean("block-protection.land-owner-bypass", false),
              p.hasPermission("landward.admin.protection.bypass"))
          || !plugin.adminBypasses().isEnabled(p.getUniqueId())
              && !lands.permissions(p.getUniqueId(), source.landId()).containers()) return true;
    }
    return false;
  }

  public boolean mechanical(BlockKey b) {
    if (!enabled()) return false;
    if (uncertain(b) || lands.personalProtections().at(b) != null) return true;
    var state = automation(b);
    return state.uncertain() || state.owner() != null;
  }

  public void tell(Player p, String key) {
    if (storageUnavailable && key.equals("locked")) key = "storage-error";
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

  public void maintenance(Player p, Checked action, Runnable success) {
    enqueue(p, action, success, () -> {}, false);
  }

  private void submit(Player p, Checked action, Runnable success, Runnable cleanup) {
    enqueue(p, action, success, cleanup, true);
  }

  private void enqueue(
      Player p, Checked action, Runnable success, Runnable cleanup, boolean requiresEnabled) {
    if (closed || requiresEnabled && !enabled()) {
      cleanup.run();
      tell(p, "disabled");
      return;
    }
    try {
      writer.execute(() -> {
        try {
          if (closed || requiresEnabled && !enabled()) return;
          action.run();
          plugin
              .scheduler()
              .player(
                  p,
                  () -> {
                    if (!closed && (!requiresEnabled || enabled())) success.run();
                  });
        } catch (Exception e) {
          if (e instanceof io.github.devkruidenier.landward.storage.StorageException)
            failed(
                lands.personalProtections().all().stream()
                    .flatMap(lock -> lock.blocks().stream())
                    .toList());
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
          resolved(keys, marker);
          if (committed != null) committed.run();
        } catch (Exception e) {
          failed(keys);
          plugin
              .getLogger()
              .log(
                  java.util.logging.Level.SEVERE,
                  "Cannot persist block ownership at "
                      + keys
                      + "; locations remain quarantined across restarts until verified recovery",
                  e);
        }
      });
    } catch (RejectedExecutionException e) {
      failed(keys);
      plugin
          .getLogger()
          .severe("Personal protection writer is full; locations quarantined: " + keys);
    }
  }

  /**
   * Auto-protection retains its reservation through durable ownership and lock writes, and does not
   * depend on the placing player remaining online.
   */
  public void automatic(
      Player player,
      ProtectionActor actor,
      UUID land,
      String material,
      List<BlockKey> unit,
      List<BlockPlacement> placements) {
    UUID marker = UUID.randomUUID();
    synchronized (markers) {
      unit.forEach(b -> uncertain.put(b, marker));
    }
    try {
      writer.execute(() -> {
        try {
          for (var placement : placements) lands.recordPlacement(placement);
          if (enabled()) {
            if (unit.stream().anyMatch(b -> !marker.equals(uncertain.get(b))))
              throw new IllegalArgumentException("changed");
            var generations = new HashMap<BlockKey, UUID>();
            for (var b : unit) {
              var placement = lands.personalProtections().placement(b);
              if (placement != null) generations.put(b, placement.generation());
            }
            var saved =
                lands.protect(
                    actor,
                    land,
                    material,
                    unit,
                    generations,
                    plugin.getConfig().getInt("block-protection.max-blocks-per-player", -1));
            seal(saved);
          }
          resolved(unit, marker);
        } catch (IllegalArgumentException rejected) {
          unit.forEach(b -> uncertain.remove(b, marker));
          plugin
              .scheduler()
              .player(
                  player,
                  () ->
                      tell(
                          player,
                          Set.of("limit", "unknown-owner", "changed", "denied")
                                  .contains(rejected.getMessage())
                              ? rejected.getMessage()
                              : "changed"));
        } catch (Exception error) {
          failed(unit);
          plugin
              .getLogger()
              .log(
                  java.util.logging.Level.SEVERE,
                  "Automatic personal protection failed at "
                      + unit
                      + "; locations remain quarantined",
                  error);
        }
      });
    } catch (RejectedExecutionException error) {
      failed(unit);
      plugin
          .getLogger()
          .severe("Automatic personal protection queue is full; locations quarantined: " + unit);
    }
  }

  public void expandChest(
      ProtectionActor actor,
      BlockProtection lock,
      BlockPlacement placed,
      org.bukkit.block.data.type.Chest shape) {
    var unit = new ArrayList<>(lock.blocks());
    unit.add(placed.position());
    resizeStorage(
        unit,
        () -> lands.extendPersonalChest(actor, lock.id(), lock.revision(), placed),
        saved -> refreshChest(saved, placed.position(), shape));
  }

  public void breakStorage(ProtectionActor actor, BlockProtection lock, BlockKey destroyed) {
    resizeStorage(
        lock.blocks(),
        () -> lands.breakPersonalStorage(actor, lock.id(), lock.revision(), destroyed),
        saved -> refreshChest(saved, null, null));
  }

  /** Reserve both halves before returning to vanilla; commit ownership and shape together. */
  private void resizeStorage(
      List<BlockKey> unit, Callable<BlockProtection> change, Consumer<BlockProtection> refresh) {
    UUID marker = UUID.randomUUID();
    synchronized (markers) {
      unit.forEach(b -> uncertain.put(b, marker));
    }
    try {
      writer.execute(() -> {
        try {
          if (unit.stream().anyMatch(b -> !marker.equals(uncertain.get(b))))
            throw new IllegalArgumentException("changed");
          var saved = change.call();
          refresh.accept(saved);
          resolved(unit, marker);
        } catch (Exception failure) {
          failed(unit);
          plugin
              .getLogger()
              .log(
                  java.util.logging.Level.SEVERE,
                  "Cannot safely resize personal storage protection at "
                      + unit
                      + "; locations remain quarantined",
                  failure);
        }
      });
    } catch (RejectedExecutionException failure) {
      failed(unit);
      plugin
          .getLogger()
          .log(
              java.util.logging.Level.SEVERE,
              "Personal storage resize queue is full at " + unit,
              failure);
    }
  }

  private void refreshChest(
      BlockProtection saved, BlockKey placed, org.bukkit.block.data.type.Chest shape) {
    if (saved == null) return;
    for (var key : saved.blocks()) {
      var world = Bukkit.getWorld(key.world());
      if (world == null) continue;
      plugin
          .scheduler()
          .atChunk(
              world,
              key.x() >> 4,
              key.z() >> 4,
              () -> {
                var current = lands.personalProtections().get(saved.id());
                if (current == null || current.revision() != saved.revision()) return false;
                var block = world.getBlockAt(key.x(), key.y(), key.z());
                if (!block.getType().name().equals(saved.material())
                    || !(block.getBlockData() instanceof org.bukkit.block.data.type.Chest chest))
                  return false;
                if (saved.blocks().size() == 1)
                  chest.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
                else {
                  chest.setFacing(shape.getFacing());
                  var opposite =
                      shape.getType() == org.bukkit.block.data.type.Chest.Type.LEFT
                          ? org.bukkit.block.data.type.Chest.Type.RIGHT
                          : org.bukkit.block.data.type.Chest.Type.LEFT;
                  chest.setType(key.equals(placed) ? shape.getType() : opposite);
                }
                block.setBlockData(chest, false);
                return true;
              });
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
                            BlockProtection saved;
                            try {
                              saved =
                                  lands.protect(
                                      currentActor,
                                      selected.land(),
                                      selected.material(),
                                      selected.blocks(),
                                      selected.generations(),
                                      plugin
                                          .getConfig()
                                          .getInt("block-protection.max-blocks-per-player", -1));
                            } catch (
                                io.github.devkruidenier.landward.storage.StorageException
                                    storageFailure) {
                              failed(selected.blocks());
                              throw storageFailure;
                            }
                            seal(saved);
                            plugin
                                .scheduler()
                                .player(
                                    p,
                                    () -> {
                                      if (enabled()) success.accept(saved);
                                    });
                          },
                          () -> tell(p, "created"),
                          () ->
                              selected
                                  .blocks()
                                  .forEach(b -> {
                                    if (quarantine != null && !quarantine.locations().contains(b))
                                      uncertain.remove(b, marker);
                                  }));
                    }));
  }

  private void lifecycleCleanup(
      UUID land, io.github.devkruidenier.landward.model.ChunkPosition chunk) {
    if (quarantine == null || quarantine.locations().isEmpty()) return;
    var affected = new HashSet<BlockKey>();
    for (var key : quarantine.locations())
      if ((chunk == null || key.chunk().equals(chunk))
          && lands.landAt(key.chunk()).map(l -> l.id().equals(land)).orElse(false))
        affected.add(key);
    for (var lock : lands.personalProtections().all())
      if (lock.landId().equals(land)
          && (chunk == null || lock.blocks().stream().anyMatch(b -> b.chunk().equals(chunk))))
        affected.addAll(lock.blocks());
    try {
      resolvedRemoval(affected);
    } catch (java.io.IOException error) {
      quarantineUnavailable = true;
      plugin
          .getLogger()
          .log(
              java.util.logging.Level.SEVERE,
              "Cannot clear failure markers after committed land/chunk cleanup",
              error);
    }
  }

  private void failed(Collection<BlockKey> blocks) {
    blocks.forEach(b -> uncertain.put(b, UUID.randomUUID()));
    try {
      if (quarantine == null) throw new java.io.IOException("Quarantine journal unavailable");
      quarantine.fail(blocks);
    } catch (java.io.IOException error) {
      quarantineUnavailable = true;
      plugin
          .getLogger()
          .log(
              java.util.logging.Level.SEVERE,
              "Cannot persist personal protection failure journal; player-land block access is"
                  + " denied until administrator recovery",
              error);
    }
  }

  private void resolved(Collection<BlockKey> blocks, UUID marker) {
    var current = blocks.stream().filter(b -> marker.equals(uncertain.get(b))).toList();
    try {
      if (quarantine == null) throw new java.io.IOException("Quarantine journal unavailable");
      quarantine.resolve(current);
      current.forEach(b -> uncertain.remove(b, marker));
    } catch (java.io.IOException error) {
      quarantineUnavailable = true;
      plugin
          .getLogger()
          .log(
              java.util.logging.Level.SEVERE,
              "Cannot clear verified protection failure journal",
              error);
    }
  }

  /** Used only after an explicitly confirmed and committed administrative lock removal. */
  public void resolvedRemoval(Collection<BlockKey> blocks) throws java.io.IOException {
    if (quarantine == null) throw new java.io.IOException("Quarantine journal unavailable");
    quarantine.resolve(blocks);
    blocks.forEach(uncertain::remove);
  }

  private void seal(BlockProtection saved) {
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
                  if (!enabled() || lands.personalProtections().get(saved.id()) == null)
                    return false;
                  var block = world.getBlockAt(key.x(), key.y(), key.z());
                  var data = block.getBlockData();
                  if (data instanceof org.bukkit.block.data.Openable open) {
                    open.setOpen(false);
                    if (data instanceof org.bukkit.block.data.Powerable power)
                      power.setPowered(false);
                    block.setBlockData(data, false);
                  }
                  return true;
                });
    }
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
