package io.github.devkruidenier.landward.metrics;

import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.model.LandWarp;
import io.github.devkruidenier.landward.storage.sqlite.SqliteLandStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UsageSnapshotTest {
    @TempDir Path directory;

    @Test void countBucketsHaveNoGapsOrOverlaps() {
        long[] values = {0, 1, 10, 11, 50, 51, 100, 101, 500, 501, Long.MAX_VALUE};
        String[] expected = {"0", "1-10", "1-10", "11-50", "11-50", "51-100", "51-100", "101-500", "101-500", "501+", "501+"};
        for (int i = 0; i < values.length; i++) assertEquals(expected[i], UsageSnapshot.bucket(values[i]));
        assertThrows(IllegalArgumentException.class, () -> UsageSnapshot.bucket(-1));
    }

    @Test void configurationCannotBecomeFreeTextTelemetry() {
        assertEquals("nl_NL", UsageSnapshot.language("nl-nl", Set.of("nl_NL")));
        assertEquals("en_US", UsageSnapshot.language("secret.server.example", Set.of("nl_NL")));
        assertEquals("en_US", UsageSnapshot.language(null, Set.of("en_US")));
        assertEquals("free", UsageSnapshot.paymentMode("secret.server.example"));
        assertEquals("experience", UsageSnapshot.paymentMode("EXPERIENCE"));
        assertEquals("vault", UsageSnapshot.paymentMode("vault"));
    }

    @Test void snapshotIsDetachedAndIntegrationPayloadContainsOnlyKnownCategories() {
        var input = new HashMap<String, Boolean>();
        input.put("WorldGuard", true);
        input.put("private-server.example", true);
        var snapshot = new UsageSnapshot(Map.of("player_lands", "1-10"), input);
        input.put("WorldGuard", false);
        var payload = snapshot.integrationChart();
        assertEquals(UsageSnapshot.INTEGRATIONS, payload.keySet());
        assertEquals(Map.of("Enabled", 1), payload.get("WorldGuard"));
        assertEquals(Map.of("Disabled", 1), payload.get("Vault"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.charts().put("bad", "value"));
    }

    @Test void countsTrackClaimsWarpsAndSafeZonesThroughDeletionAndReload() throws Exception {
        try (var storage = new SqliteLandStorage(directory.resolve("metrics.db"))) {
            storage.initialize();
            var lands = new LandManager(storage);
            lands.load();
            assertArrayEquals(new long[]{0,0,0,0}, lands.usageCounts());
            UUID owner = UUID.randomUUID(), world = UUID.randomUUID();
            var land = lands.create(owner, "Private Name", new ChunkPosition(world, 0, 0)).land();
            lands.claim(owner, new ChunkPosition(world, 1, 0), -1, true);
            lands.claimAdministration("Private Safe Zone", new ChunkPosition(world, 9, 9));
            lands.addWarp(owner, land.id(), new LandWarp(UUID.randomUUID(), "Private Warp", world, 1, 64, 1, 0, 0), 3);
            assertArrayEquals(new long[]{1,3,1,1}, lands.usageCounts());
            lands.load();
            assertArrayEquals(new long[]{1,3,1,1}, lands.usageCounts());
            lands.unclaim(owner, new ChunkPosition(world, 0, 0));
            assertArrayEquals(new long[]{1,2,1,0}, lands.usageCounts());
            lands.deleteOwnedLand(owner);
            assertArrayEquals(new long[]{0,1,1,0}, lands.usageCounts());
        }
    }
}
