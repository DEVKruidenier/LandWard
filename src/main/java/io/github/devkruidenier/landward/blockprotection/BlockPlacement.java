package io.github.devkruidenier.landward.blockprotection;

import java.util.UUID;

/** Each placement has a new generation, including replacing a block with the same material. */
public record BlockPlacement(
    BlockKey position, UUID landId, UUID owner, String material, UUID generation) {
  public BlockPlacement {
    java.util.Objects.requireNonNull(position);
    java.util.Objects.requireNonNull(landId);
    java.util.Objects.requireNonNull(owner);
    java.util.Objects.requireNonNull(material);
    java.util.Objects.requireNonNull(generation);
  }
}
