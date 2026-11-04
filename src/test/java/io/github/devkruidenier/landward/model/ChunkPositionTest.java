package io.github.devkruidenier.landward.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkPositionTest {

    @Test
    void mapsNegativeChunkCoordinatesToAnExactSixteenBlockArea() {
        ChunkPosition position = new ChunkPosition(UUID.randomUUID(), -30, -7);

        assertEquals(-480, position.minBlockX());
        assertEquals(-464, position.maxBlockXExclusive());
        assertEquals(-112, position.minBlockZ());
        assertEquals(-96, position.maxBlockZExclusive());
    }

    @Test
    void mapsPositiveChunkCoordinatesToAnExactSixteenBlockArea() {
        ChunkPosition position = new ChunkPosition(UUID.randomUUID(), 4, 9);

        assertEquals(64, position.minBlockX());
        assertEquals(80, position.maxBlockXExclusive());
        assertEquals(144, position.minBlockZ());
        assertEquals(160, position.maxBlockZExclusive());
    }
}
