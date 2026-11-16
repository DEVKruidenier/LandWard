package io.github.devkruidenier.landward.integration.placeholder;

import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.model.Land;

/** Location-only resolution; never falls back to the viewer's owned land. */
final class CurrentClaimName {
    private CurrentClaimName() {}
    static String resolve(LandManager lands, ChunkPosition position) {
        return lands.landAt(position).map(Land::name).orElse("");
    }
}
