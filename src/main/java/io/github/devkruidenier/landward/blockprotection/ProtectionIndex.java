package io.github.devkruidenier.landward.blockprotection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Read paths are O(1), do no I/O, and never acquire the JDBC mutation monitor. */
public final class ProtectionIndex {
  private final java.util.concurrent.atomic.AtomicLong revision =
      new java.util.concurrent.atomic.AtomicLong();

  public long revision() {
    return revision.get();
  }

  private final Map<io.github.devkruidenier.landward.model.ChunkPosition, Set<BlockKey>> chunks =
      new ConcurrentHashMap<>();
  private final Map<UUID, BlockProtection> locks = new ConcurrentHashMap<>();
  private final Map<BlockKey, UUID> locations = new ConcurrentHashMap<>();
  private final Map<BlockKey, BlockPlacement> placements = new ConcurrentHashMap<>();

  public BlockProtection at(BlockKey b) {
    UUID id = locations.get(b);
    return id == null ? null : locks.get(id);
  }

  public BlockProtection get(UUID id) {
    return locks.get(id);
  }

  public BlockPlacement placement(BlockKey b) {
    return placements.get(b);
  }

  public List<BlockProtection> all() {
    return List.copyOf(locks.values());
  }

  public List<BlockPlacement> placements() {
    return List.copyOf(placements.values());
  }

  public void put(BlockProtection p) {
    locks.put(p.id(), p);
    p.blocks()
        .forEach(b -> {
          locations.put(b, p.id());
          indexed(b);
        });
    revision.incrementAndGet();
  }

  public void put(BlockPlacement p) {
    placements.put(p.position(), p);
    indexed(p.position());
    revision.incrementAndGet();
  }

  public void remove(UUID id) {
    var p = locks.remove(id);
    if (p != null)
      p.blocks()
          .forEach(b -> {
            locations.remove(b, id);
            unindexed(b);
          });
    revision.incrementAndGet();
  }

  public void forget(BlockKey b) {
    var p = at(b);
    if (p != null) remove(p.id());
    placements.remove(b);
    unindexed(b);
    revision.incrementAndGet();
  }

  public void clear() {
    locks.clear();
    locations.clear();
    placements.clear();
    chunks.clear();
    revision.incrementAndGet();
  }

  public void prune(Predicate<BlockProtection> keep, Predicate<BlockPlacement> keepPlacement) {
    all().stream().filter(keep.negate()).forEach(p -> remove(p.id()));
    for (var p : placements())
      if (!keepPlacement.test(p)) {
        placements.remove(p.position(), p);
        revision.incrementAndGet();
        unindexed(p.position());
      }
  }

  public Set<BlockKey> inChunk(io.github.devkruidenier.landward.model.ChunkPosition chunk) {
    return Set.copyOf(chunks.getOrDefault(chunk, Set.of()));
  }

  private void indexed(BlockKey b) {
    chunks.computeIfAbsent(b.chunk(), k -> ConcurrentHashMap.newKeySet()).add(b);
  }

  private void unindexed(BlockKey b) {
    if (at(b) == null && !placements.containsKey(b)) {
      var keys = chunks.get(b.chunk());
      if (keys != null) keys.remove(b);
    }
  }

  public void removeMember(UUID land, UUID member) {
    for (var p : all())
      if (p.landId().equals(land)) {
        if (p.owner().equals(member)) remove(p.id());
        else if (p.shared().contains(member)) {
          var shared = new HashSet<>(p.shared());
          shared.remove(member);
          put(p.sharing(shared));
        }
      }
    for (var p : placements())
      if (p.landId().equals(land) && p.owner().equals(member)) {
        placements.remove(p.position(), p);
        revision.incrementAndGet();
        unindexed(p.position());
      }
  }
}
