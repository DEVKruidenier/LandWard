package io.github.devkruidenier.landward.claim;

import io.github.devkruidenier.landward.model.*;
import io.github.devkruidenier.landward.storage.sqlite.SqliteLandStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LandWarpLifecycleTest {
    @TempDir Path directory;
    @Test void validatesAuthorizationLimitsRenameTransferUnclaimAndDeletion() throws Exception {
        var storage = new SqliteLandStorage(directory.resolve("lands.db")); storage.initialize();
        var lands = new LandManager(storage); lands.load();
        UUID owner = UUID.randomUUID(), member = UUID.randomUUID(), visitor = UUID.randomUUID(), world = UUID.randomUUID();
        var home = new ChunkPosition(world, 0, 0); var extra = new ChunkPosition(world, 1, 0);
        var land = lands.create(owner, "Amsterdam", home).land(); lands.claim(owner, extra, 10, true);
        lands.addMember(owner, member);
        var warp = new LandWarp(UUID.randomUUID(), "Home", world, 18.25, 80.5, .75, 100, -22);
        assertThrows(IllegalArgumentException.class, () -> lands.addWarp(visitor, land.id(), warp, 3));
        assertThrows(IllegalArgumentException.class, () -> lands.addWarp(member, land.id(), warp, 3));
        assertThrows(IllegalArgumentException.class, () -> lands.addWarp(owner, land.id(), warp, 0));
        lands.addWarp(owner, land.id(), warp, 3);
        assertTrue(lands.canUseWarps(owner, land.id())); assertTrue(lands.canUseWarps(member, land.id())); assertFalse(lands.canUseWarps(visitor, land.id()));
        assertThrows(IllegalArgumentException.class, () -> lands.addWarp(owner, land.id(), new LandWarp(UUID.randomUUID(), "HOME", world, 1, 80, 1, 0, 0), 3));
        assertThrows(IllegalArgumentException.class, () -> lands.addWarp(owner, land.id(), new LandWarp(UUID.randomUUID(), "Outside", world, 1000, 80, 1, 0, 0), 3));
        lands.renameWarp(owner, land.id(), warp.id(), "Spawn");
        assertEquals("Spawn", lands.features(land.id()).warps().getFirst().name());
        UUID replacementOwner = UUID.randomUUID();
        lands.transferOwnership(land.id(), replacementOwner);
        assertTrue(lands.canUseWarps(replacementOwner, land.id()));
        assertEquals(1, lands.features(land.id()).warps().size());
        assertThrows(IllegalArgumentException.class, () -> lands.addWarp(replacementOwner, land.id(),
                new LandWarp(UUID.randomUUID(), "Extra", world, 1, 80, 1, 0, 0), 0));
        lands.transferOwnership(land.id(), owner);
        lands.removeMember(owner, member); assertFalse(lands.canUseWarps(member, land.id()));
        var reloaded = new LandManager(storage); reloaded.load(); assertEquals(lands.features(land.id()), reloaded.features(land.id()));
        lands.unclaim(owner, extra); assertTrue(lands.features(land.id()).warps().isEmpty());
        assertTrue(storage.load().features().getFirst().warps().isEmpty());
        lands.addWarp(owner, land.id(), new LandWarp(UUID.randomUUID(), "New", world, 1, 80, 1, 0, 0), 1);
        lands.adminUnclaim(home); assertTrue(storage.load().features().isEmpty());
    }
    @Test void newSafeZonesDenyDamageWhileLegacyLandsRetainSurvivalDamage() throws Exception {
        var storage = new SqliteLandStorage(directory.resolve("safe.db")); storage.initialize();
        UUID id = UUID.randomUUID();
        var old = new Land(id, UUID.randomUUID(), "Legacy", java.time.Instant.ofEpochMilli(1000), MemberPermissions.FULL, LandProtectionSettings.DEFAULT);
        storage.insertAdminLandWithClaim(old, new ClaimedChunk(new ChunkPosition(UUID.randomUUID(), 0, 0), id));
        var lands = new LandManager(storage); lands.load();
        assertTrue(lands.features(id).environment().allows(EnvironmentSettings.Rule.FALL_DAMAGE));
        assertFalse(lands.features(id).environment().allows(EnvironmentSettings.Rule.FIRE_SPREAD));
        lands.deleteAdministrativeLand();
        var safe = lands.claimAdministration(new ChunkPosition(UUID.randomUUID(), 0, 0)).land();
        assertFalse(lands.features(safe.id()).environment().allows(EnvironmentSettings.Rule.FALL_DAMAGE));
        lands.environment(safe.id(), lands.features(safe.id()).environment().with(EnvironmentSettings.Rule.FALL_DAMAGE, true));
        lands.load(); assertTrue(lands.features(safe.id()).environment().allows(EnvironmentSettings.Rule.FALL_DAMAGE));
    }
    @Test void concurrentWarpCreationAndUnclaimCannotLeaveAnOrphan() throws Exception {
        var storage = new SqliteLandStorage(directory.resolve("race.db")); storage.initialize();
        var lands = new LandManager(storage); lands.load();
        try (var tasks = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 20; i++) {
                UUID owner = UUID.randomUUID(), world = UUID.randomUUID();
                var home = new ChunkPosition(world, 0, 0); var extra = new ChunkPosition(world, 1, 0);
                var land = lands.create(owner, "Race " + i, home).land(); lands.claim(owner, extra, 10, true);
                var start = new java.util.concurrent.CountDownLatch(1);
                var created = tasks.submit(() -> {
                    start.await();
                    try { lands.addWarp(owner, land.id(), new LandWarp(UUID.randomUUID(), "Home", world, 17, 80, 1, 0, 0), 3); }
                    catch (IllegalArgumentException removedBeforeCreation) { }
                    return null;
                });
                var removed = tasks.submit(() -> { start.await(); lands.unclaim(owner, extra); return null; });
                start.countDown(); created.get(); removed.get();
                assertTrue(lands.features(land.id()).warps().isEmpty());
                assertTrue(storage.load().features().stream().filter(value -> value.landId().equals(land.id())).findFirst().orElseThrow().warps().isEmpty());
                lands.deleteOwnedLand(owner);
            }
        }
    }
}
