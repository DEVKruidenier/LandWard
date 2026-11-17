package io.github.devkruidenier.landward.model;

import java.util.List;
import java.util.UUID;
import java.util.HashSet;

/** Durable extensions owned by a land, independent of its owner UUID. */
public record LandFeatures(UUID landId, EnvironmentSettings environment, List<LandWarp> warps) {
    public LandFeatures {
        java.util.Objects.requireNonNull(landId); java.util.Objects.requireNonNull(environment);
        warps = warps.stream().sorted(java.util.Comparator.comparing(warp -> warp.id().toString())).toList();
        var names = new HashSet<String>(); var ids = new HashSet<UUID>();
        for (var warp : warps) if (!names.add(warp.normalizedName()) || !ids.add(warp.id()))
            throw new IllegalArgumentException("Duplicate warp");
    }
}
