package io.github.devkruidenier.landward.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LandFeaturesTest {
    @Test void defaultsKeepDestructiveActionsDeniedAndSurvivalDamageExplicit() {
        for (boolean admin : new boolean[]{false, true}) {
            var settings = EnvironmentSettings.defaults(admin);
            for (var rule : EnvironmentSettings.Rule.values()) {
                boolean flow = rule == EnvironmentSettings.Rule.WATER_FLOW || rule == EnvironmentSettings.Rule.LAVA_FLOW;
                boolean damage = rule == EnvironmentSettings.Rule.FALL_DAMAGE || rule == EnvironmentSettings.Rule.EXPLOSION_PLAYER_DAMAGE
                        || rule == EnvironmentSettings.Rule.FIRE_PLAYER_DAMAGE || rule == EnvironmentSettings.Rule.LAVA_PLAYER_DAMAGE;
                assertEquals(flow || damage && !admin, settings.allows(rule), rule.name());
                assertEquals(!settings.allows(rule), settings.with(rule, !settings.allows(rule)).allows(rule));
                assertEquals(flow || damage && !admin, settings.allows(rule), "Original is immutable");
            }
        }
    }
    @Test void validatesNamesDuplicatesCoordinatesAndNegativeChunkBoundaries() {
        var world = UUID.randomUUID();
        var warp = new LandWarp(UUID.randomUUID(), " Home ", world, -0.1, 80, -16.1, 90, 30);
        assertEquals(new ChunkPosition(world, -1, -2), warp.chunk());
        assertEquals("Home", warp.name());
        assertEquals(warp.id(), warp.renamed("Spawn").id());
        assertThrows(IllegalArgumentException.class, () -> warp.renamed(" "));
        assertThrows(IllegalArgumentException.class, () -> warp.renamed("../bad"));
        assertThrows(IllegalArgumentException.class, () -> new LandWarp(UUID.randomUUID(), "Bad", world, Double.NaN, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new LandFeatures(UUID.randomUUID(), EnvironmentSettings.legacy(),
                List.of(warp, new LandWarp(UUID.randomUUID(), "HOME", world, 0, 80, 0, 0, 0))));
    }
}
