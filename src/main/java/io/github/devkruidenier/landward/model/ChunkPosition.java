package io.github.devkruidenier.landward.model;

import java.util.UUID;

public record ChunkPosition(UUID worldId, int x, int z) {
}
