package io.github.devkruidenier.landward.integration.plan;

import com.djrapitops.plan.extension.extractor.ExtensionExtractor;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.storage.sqlite.SqliteLandStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandWardPlanExtensionTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void exposesValidPlanProvidersAndCurrentLandData() throws Exception {
        SqliteLandStorage storage = new SqliteLandStorage(temporaryDirectory.resolve("plan.db"));
        storage.initialize();
        LandManager lands = new LandManager(storage);
        lands.load();

        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        lands.create(owner, "Plan Test", new ChunkPosition(world, 0, 0));
        lands.claim(owner, new ChunkPosition(world, 1, 0), -1, true);
        lands.addMember(owner, member);
        lands.claimAdministration("Safe Zone", new ChunkPosition(world, 10, 10));

        LandWardPlanExtension extension = new LandWardPlanExtension(lands);
        new ExtensionExtractor(extension).validateAnnotations();

        assertEquals(1, extension.playerLands());
        assertEquals(2, extension.playerClaimedChunks());
        assertEquals(1, extension.safeZones());
        assertEquals(1, extension.safeZoneChunks());
        assertEquals(1, extension.uniqueLandOwners());
        assertEquals(1, extension.landMemberships());
        assertTrue(extension.ownedLand(owner).startsWith("Plan Test (2 chunks)"));
        assertTrue(extension.memberLandNames(member).startsWith("Plan Test (2 chunks)"));
    }
}
