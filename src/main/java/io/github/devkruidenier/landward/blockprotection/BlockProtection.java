package io.github.devkruidenier.landward.blockprotection;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** One logical lock: one block, both chest halves, or both halves of a door. */
public record BlockProtection(
    UUID id,
    UUID landId,
    UUID owner,
    String material,
    List<BlockKey> blocks,
    Set<UUID> shared,
    long created,
    long revision) {
  public BlockProtection {
    java.util.Objects.requireNonNull(id);
    java.util.Objects.requireNonNull(landId);
    java.util.Objects.requireNonNull(owner);
    java.util.Objects.requireNonNull(material);
    blocks =
        blocks.stream()
            .sorted(
                java.util.Comparator.comparingInt(BlockKey::x)
                    .thenComparingInt(BlockKey::y)
                    .thenComparingInt(BlockKey::z))
            .toList();
    shared = Set.copyOf(shared);
    var unitWorld = blocks.isEmpty() ? null : blocks.getFirst().world();
    if (blocks.isEmpty()
        || blocks.size() > 2
        || Set.copyOf(blocks).size() != blocks.size()
        || blocks.stream().anyMatch(b -> !b.world().equals(unitWorld)))
      throw new IllegalArgumentException("Invalid protection unit");
  }

  public BlockProtection sharing(Set<UUID> members) {
    return new BlockProtection(id, landId, owner, material, blocks, members, created, revision + 1);
  }

  public boolean storage() {
    return material.equals("CHEST")
        || material.equals("TRAPPED_CHEST")
        || material.endsWith("COPPER_CHEST")
        || material.equals("BARREL")
        || material.endsWith("SHULKER_BOX");
  }
}
