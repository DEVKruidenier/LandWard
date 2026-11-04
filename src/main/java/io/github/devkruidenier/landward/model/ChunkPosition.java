package io.github.devkruidenier.landward.model;

import java.util.UUID;

public record ChunkPosition(UUID worldId, int x, int z) {

    /** Inclusive world-block X coordinate of this chunk's western edge. */
    public int minBlockX() { return x << 4; }

    /** Exclusive world-block X coordinate of this chunk's eastern edge. */
    public int maxBlockXExclusive() { return minBlockX() + 16; }

    /** Inclusive world-block Z coordinate of this chunk's northern edge. */
    public int minBlockZ() { return z << 4; }

    /** Exclusive world-block Z coordinate of this chunk's southern edge. */
    public int maxBlockZExclusive() { return minBlockZ() + 16; }
}
