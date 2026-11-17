package io.github.devkruidenier.landward.model;

import java.util.UUID;
import java.util.Locale;

/** Stable identity survives renaming; precise destination never references a Bukkit world object. */
public record LandWarp(UUID id, String name, UUID worldId, double x, double y, double z, float yaw, float pitch) {
    public LandWarp {
        java.util.Objects.requireNonNull(id); java.util.Objects.requireNonNull(worldId);
        name = name == null ? "" : name.strip();
        if (!name.matches("[\\p{L}\\p{N} _-]{1,24}")) throw new IllegalArgumentException("Invalid warp name");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000)
            throw new IllegalArgumentException("Invalid warp destination");
    }
    public String normalizedName() { return name.toLowerCase(Locale.ROOT); }
    public ChunkPosition chunk() { return new ChunkPosition(worldId, ((int)Math.floor(x)) >> 4, ((int)Math.floor(z)) >> 4); }
    public LandWarp renamed(String value) { return new LandWarp(id, value, worldId, x, y, z, yaw, pitch); }
}
