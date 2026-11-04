package io.github.devkruidenier.landward.model;

import java.util.UUID;

public record ClaimedChunk(ChunkPosition position, UUID landId) {
}
