package io.github.devkruidenier.landward.blockprotection;

import io.github.devkruidenier.landward.model.ChunkPosition;
import java.util.UUID;

/** A world UUID and integer block position; never a mutable Bukkit Location. */
public record BlockKey(UUID world, int x, int y, int z) {
  public BlockKey {
    java.util.Objects.requireNonNull(world);
  }

  public ChunkPosition chunk() {
    return new ChunkPosition(world, x >> 4, z >> 4);
  }

  public String label() {
    return x + ", " + y + ", " + z;
  }
}
