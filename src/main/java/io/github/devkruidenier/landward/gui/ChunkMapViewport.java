package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.model.ChunkPosition;
import java.util.Objects;

/** One menu session: movement is bounded against its fixed origin, never the last click. */
record ChunkMapViewport(ChunkPosition origin, int offsetX, int offsetZ) {
    ChunkMapViewport { Objects.requireNonNull(origin); }
    static int limit(int configured) { return Math.clamp(configured, 0, 1024); }
    ChunkMapViewport move(int dx, int dz, int configured) {
        int maximum = limit(configured);
        return new ChunkMapViewport(origin,
                (int)Math.clamp((long)offsetX + dx, -maximum, maximum),
                (int)Math.clamp((long)offsetZ + dz, -maximum, maximum));
    }
    ChunkPosition center() {
        return new ChunkPosition(origin.worldId(), Math.addExact(origin.x(), offsetX), Math.addExact(origin.z(), offsetZ));
    }
    ChunkPosition chunk(int slot) {
        if (slot < 0 || slot >= 45) throw new IllegalArgumentException("Not a map chunk slot");
        var center = center();
        return new ChunkPosition(center.worldId(), center.x() + slot % 9 - 4, center.z() + slot / 9 - 2);
    }
}
