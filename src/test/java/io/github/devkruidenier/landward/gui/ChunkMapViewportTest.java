package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.model.ChunkPosition;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ChunkMapViewportTest {
    private final ChunkPosition origin = new ChunkPosition(UUID.randomUUID(), -10, 20);
    @Test void repeatedMovesCannotResetTheOriginOrEscapeThirtyChunks() {
        var viewport = new ChunkMapViewport(origin, 0, 0);
        for (int i = 0; i < 100; i++) viewport = viewport.move(1, -1, 30);
        assertEquals(origin, viewport.origin());
        assertEquals(new ChunkPosition(origin.worldId(), 20, -10), viewport.center());
        assertEquals(viewport, viewport.move(1, -1, 30));
        for (int i = 0; i < 200; i++) viewport = viewport.move(-1, 1, 30);
        assertEquals(-30, viewport.offsetX()); assertEquals(30, viewport.offsetZ());
    }
    @Test void allFortyFiveCellsUseTheShiftedWorldAndGridCoordinates() {
        var viewport = new ChunkMapViewport(origin, 3, -4);
        assertEquals(viewport.center(), viewport.chunk(22));
        assertEquals(new ChunkPosition(origin.worldId(), -11, 14), viewport.chunk(0));
        assertEquals(new ChunkPosition(origin.worldId(), -3, 18), viewport.chunk(44));
        assertThrows(IllegalArgumentException.class, () -> viewport.chunk(45));
        assertThrows(IllegalArgumentException.class, () -> viewport.chunk(-1));
    }
    @Test void configurationChangesAndOverflowSizedStepsStayBounded() {
        var viewport = new ChunkMapViewport(origin, 30, -30).move(0, 0, 5);
        assertEquals(5, viewport.offsetX()); assertEquals(-5, viewport.offsetZ());
        assertEquals(new ChunkMapViewport(origin, 0, 0), viewport.move(0, 0, 0));
        assertEquals(0, ChunkMapViewport.limit(-1)); assertEquals(1024, ChunkMapViewport.limit(Integer.MAX_VALUE));
        assertEquals(30, viewport.move(Integer.MAX_VALUE, 0, 30).offsetX());
    }
}
