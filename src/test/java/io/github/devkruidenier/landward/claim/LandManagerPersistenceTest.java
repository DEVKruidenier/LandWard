package io.github.devkruidenier.landward.claim;

import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.storage.sqlite.SqliteLandStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandManagerPersistenceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsClaimsAndLoadsThemAfterRestart() throws Exception {
        SqliteLandStorage storage = new SqliteLandStorage(temporaryDirectory.resolve("landward.db"));
        storage.initialize();
        LandManager manager = new LandManager(storage);
        manager.load();

        UUID owner = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        ChunkPosition initial = new ChunkPosition(world, 4, 8);
        assertEquals(LandManager.CreateStatus.CREATED, manager.create(owner, "Oak Haven", initial).status());
        assertEquals(
                LandManager.ClaimStatus.NOT_ADJACENT,
                manager.claim(owner, new ChunkPosition(world, 10, 10), 25, true).status()
        );
        assertEquals(
                LandManager.ClaimStatus.CLAIMED,
                manager.claim(owner, new ChunkPosition(world, 5, 8), 25, true).status()
        );

        LandManager reloaded = new LandManager(storage);
        reloaded.load();
        assertEquals(2, reloaded.totalClaimCount());
        assertTrue(reloaded.landAt(initial).isPresent());
        assertEquals("Oak Haven", reloaded.landAt(initial).orElseThrow().name());
    }

    @Test
    void enforcesLimitWhileMinusOneRemainsUnlimited() throws Exception {
        SqliteLandStorage storage = new SqliteLandStorage(temporaryDirectory.resolve("limits.db"));
        storage.initialize();
        LandManager manager = new LandManager(storage);
        manager.load();

        UUID owner = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        manager.create(owner, "Limit Test", new ChunkPosition(world, 0, 0));

        assertEquals(
                LandManager.ClaimStatus.LIMIT_REACHED,
                manager.claim(owner, new ChunkPosition(world, 1, 0), 1, true).status()
        );
        assertEquals(
                LandManager.ClaimStatus.CLAIMED,
                manager.claim(owner, new ChunkPosition(world, 1, 0), -1, true).status()
        );
    }
}
