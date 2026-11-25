package io.github.devkruidenier.landward.blockprotection;

import java.util.*;

/** Pure cached topology: no world access and no inference of ownership from proximity alone. */
public final class AutomationOwnership {
  private AutomationOwnership() {}

  public record Snapshot(
      long revision, Map<BlockKey, UUID> privateHoppers, Map<BlockKey, Set<UUID>> origins) {
    public Snapshot(long revision, Map<BlockKey, UUID> privateHoppers) {
      this(revision, privateHoppers, Map.of());
    }

    public Snapshot {
      privateHoppers = Map.copyOf(privateHoppers);
      origins =
          origins.entrySet().stream()
              .collect(
                  java.util.stream.Collectors.toUnmodifiableMap(
                      Map.Entry::getKey, e -> Set.copyOf(e.getValue())));
    }
  }

  public static Snapshot build(
      long revision, List<BlockPlacement> placements, List<BlockProtection> protections) {
    var hoppers = new HashMap<BlockKey, UUID>();
    var storage = new HashMap<BlockKey, UUID>();
    var storageIds = new HashMap<BlockKey, UUID>();
    var originsByHopper = new HashMap<BlockKey, Set<UUID>>();
    for (var p : placements)
      if (p.material().equals("HOPPER")) hoppers.put(p.position(), p.owner());
    for (var p : protections)
      if (storage(p.material()))
        for (var b : p.blocks()) {
          storage.put(b, p.owner());
          storageIds.put(b, p.id());
        }
    var visited = new HashSet<BlockKey>();
    var privateHoppers = new HashMap<BlockKey, UUID>();
    for (var entry : hoppers.entrySet()) {
      if (!visited.add(entry.getKey())) continue;
      UUID owner = entry.getValue();
      var pending = new ArrayDeque<BlockKey>();
      var component = new ArrayList<BlockKey>();
      var origins = new HashSet<UUID>();
      pending.add(entry.getKey());
      while (!pending.isEmpty()) {
        var b = pending.removeFirst();
        component.add(b);
        for (var neighbor : neighbors(b)) {
          if (owner.equals(storage.get(neighbor))) origins.add(storageIds.get(neighbor));
          if (owner.equals(hoppers.get(neighbor)) && visited.add(neighbor))
            pending.addLast(neighbor);
        }
      }
      if (!origins.isEmpty()) {
        var immutable = Set.copyOf(origins);
        component.forEach(b -> {
          privateHoppers.put(b, owner);
          originsByHopper.put(b, immutable);
        });
      }
    }
    return new Snapshot(revision, privateHoppers, originsByHopper);
  }

  private static boolean storage(String material) {
    return material.equals("CHEST")
        || material.equals("TRAPPED_CHEST")
        || material.equals("BARREL")
        || material.endsWith("SHULKER_BOX");
  }

  private static List<BlockKey> neighbors(BlockKey b) {
    return List.of(
        new BlockKey(b.world(), b.x() + 1, b.y(), b.z()),
            new BlockKey(b.world(), b.x() - 1, b.y(), b.z()),
        new BlockKey(b.world(), b.x(), b.y() + 1, b.z()),
            new BlockKey(b.world(), b.x(), b.y() - 1, b.z()),
        new BlockKey(b.world(), b.x(), b.y(), b.z() + 1),
            new BlockKey(b.world(), b.x(), b.y(), b.z() - 1));
  }
}
