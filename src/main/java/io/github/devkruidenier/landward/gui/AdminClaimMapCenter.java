package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.model.ClaimedChunk;
import io.github.devkruidenier.landward.model.ChunkPosition;
import java.util.Collection;
import java.util.Comparator;
import java.util.UUID;

/** Selects a remote map viewport without changing any player/world state. */
final class AdminClaimMapCenter {
    private AdminClaimMapCenter() {}

    static ChunkPosition select(ChunkPosition current, UUID selectedLand, Collection<ClaimedChunk> claims) {
        if (selectedLand == null) return current;
        return claims.stream().filter(claim -> claim.landId().equals(selectedLand))
                .map(ClaimedChunk::position)
                .min(Comparator.comparing((ChunkPosition position) -> position.worldId().toString())
                        .thenComparingInt(ChunkPosition::x).thenComparingInt(ChunkPosition::z))
                .orElse(current);
    }
}
