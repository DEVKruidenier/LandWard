package io.github.devkruidenier.landward.integration.placeholder;

import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.storage.sqlite.SqliteLandStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CurrentClaimNameTest {
    @TempDir Path directory;
    @Test void resolvesLocationWithoutChangingOwnershipAcrossTransitions() throws Exception {
        var storage = new SqliteLandStorage(directory.resolve("claims.db"));
        storage.initialize();
        var lands = new LandManager(storage);
        lands.load();
        var world = UUID.randomUUID();
        var owner = UUID.randomUUID();
        var own = new ChunkPosition(world, 0, 0);
        var other = new ChunkPosition(world, 1, 0);
        var safe = new ChunkPosition(world, 2, 0);
        var wild = new ChunkPosition(world, -1, -1);
        assertEquals("", CurrentClaimName.resolve(lands, wild));
        lands.create(owner, "Amsterdam", own);
        lands.create(UUID.randomUUID(), "Rotterdam", other);
        lands.claimAdministration("Safe Zone", safe);
        for (int i = 0; i < 2; i++) {
            assertEquals("Amsterdam", CurrentClaimName.resolve(lands, own));
            assertEquals("Rotterdam", CurrentClaimName.resolve(lands, other));
            assertEquals("Safe Zone", CurrentClaimName.resolve(lands, safe));
            assertEquals("", CurrentClaimName.resolve(lands, wild));
            assertEquals("Amsterdam", lands.ownedBy(owner).orElseThrow().name());
            assertTrue(lands.ownedBy(UUID.randomUUID()).isEmpty());
        }
    }
}
