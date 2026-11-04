package io.github.devkruidenier.landward.claim;

import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.model.LandType;
import io.github.devkruidenier.landward.model.MemberPermissions;
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

    @Test
    void persistsMemberPermissions() throws Exception {
        SqliteLandStorage storage = new SqliteLandStorage(temporaryDirectory.resolve("members.db"));
        storage.initialize();
        LandManager manager = new LandManager(storage);
        manager.load();

        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        LandManager.CreateResult created = manager.create(owner, "Member Home", new ChunkPosition(world, 0, 0));
        assertEquals(LandManager.MemberStatus.ADDED, manager.addMember(owner, member).status());
        assertEquals(
                LandManager.MemberStatus.PERMISSION_UPDATED,
                manager.updateMemberPermission(owner, member, MemberPermissions.Permission.CONTAINERS, false).status()
        );

        LandManager reloaded = new LandManager(storage);
        reloaded.load();
        assertEquals(1, reloaded.members(created.land().id()).size());
        assertTrue(!reloaded.permissions(member, created.land().id()).containers());
    }

    @Test
    void refusesToUnclaimLastChunkAndPersistsOtherUnclaims() throws Exception {
        SqliteLandStorage storage = new SqliteLandStorage(temporaryDirectory.resolve("unclaim.db"));
        storage.initialize();
        LandManager manager = new LandManager(storage);
        manager.load();
        UUID owner = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        ChunkPosition first = new ChunkPosition(world, 0, 0);
        ChunkPosition second = new ChunkPosition(world, 1, 0);
        manager.create(owner, "Unclaim Test", first);
        manager.claim(owner, second, -1, true);

        assertEquals(LandManager.UnclaimStatus.UNCLAIMED_SUCCESS, manager.unclaim(owner, second).status());
        assertEquals(LandManager.UnclaimStatus.LAST_CHUNK, manager.unclaim(owner, first).status());
        assertEquals(1, manager.totalClaimCount());
    }

    @Test
    void deletesTheFinalClaimAndAllMembersWhenAnOwnedLandIsRemoved() throws Exception {
        SqliteLandStorage storage = new SqliteLandStorage(temporaryDirectory.resolve("delete-land.db"));
        storage.initialize();
        LandManager manager = new LandManager(storage);
        manager.load();
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        ChunkPosition position = new ChunkPosition(world, 0, 0);
        var created = manager.create(owner, "Remove Me", position);
        manager.addMember(owner, member);

        assertTrue(manager.deleteOwnedLand(owner).isPresent());
        assertEquals(0, manager.totalClaimCount());
        assertTrue(manager.ownedBy(owner).isEmpty());
        assertTrue(manager.landAt(position).isEmpty());

        LandManager reloaded = new LandManager(storage);
        reloaded.load();
        assertEquals(0, reloaded.totalClaimCount());
        assertTrue(reloaded.land(created.land().id()).isEmpty());
        assertTrue(reloaded.members(created.land().id()).isEmpty());
    }

    @Test
    void administratorCanRemoveOneClaimOrDeleteTheFinalClaim() throws Exception {
        SqliteLandStorage storage = new SqliteLandStorage(temporaryDirectory.resolve("admin-unclaim.db"));
        storage.initialize();
        LandManager manager = new LandManager(storage);
        manager.load();
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        ChunkPosition first = new ChunkPosition(world, 0, 0);
        ChunkPosition second = new ChunkPosition(world, 1, 0);
        var created = manager.create(owner, "Admin Test", first);
        manager.claim(owner, second, -1, true);
        manager.addMember(owner, member);

        assertEquals(LandManager.AdminUnclaimStatus.CLAIM_REMOVED, manager.adminUnclaim(second).status());
        assertEquals(1, manager.totalClaimCount());
        assertTrue(manager.ownedBy(owner).isPresent());

        assertEquals(LandManager.AdminUnclaimStatus.LAND_DELETED, manager.adminUnclaim(first).status());
        assertEquals(0, manager.totalClaimCount());
        assertTrue(manager.land(created.land().id()).isEmpty());
        assertTrue(manager.members(created.land().id()).isEmpty());
    }

    @Test
    void administrativeClaimsPersistSeparatelyFromPlayerOwnership() throws Exception {
        SqliteLandStorage storage = new SqliteLandStorage(temporaryDirectory.resolve("admin-claims.db"));
        storage.initialize();
        LandManager manager = new LandManager(storage);
        manager.load();
        UUID world = UUID.randomUUID();
        ChunkPosition first = new ChunkPosition(world, 6, -3);
        ChunkPosition second = new ChunkPosition(world, 20, -15);

        LandManager.AdminClaimResult created = manager.claimAdministration(first);
        assertEquals(LandManager.AdminClaimStatus.CREATED, created.status());
        assertEquals(LandType.ADMIN, manager.typeOf(created.land()));
        assertTrue(manager.ownedBy(created.land().ownerId()).isEmpty());

        assertEquals(LandManager.AdminClaimStatus.CLAIMED, manager.claimAdministration(second).status());
        UUID trustedMember = UUID.randomUUID();
        assertEquals(LandManager.MemberStatus.ADDED, manager.addAdministrativeMember(trustedMember).status());
        LandManager reloaded = new LandManager(storage);
        reloaded.load();
        assertEquals(LandType.ADMIN, reloaded.typeOf(reloaded.landAt(first).orElseThrow()));
        assertEquals(LandType.ADMIN, reloaded.typeOf(reloaded.landAt(second).orElseThrow()));
        assertEquals(2, reloaded.claimCount(created.land().id()));
        assertEquals(MemberPermissions.FULL, reloaded.permissions(trustedMember, created.land().id()));
        assertEquals(LandManager.MemberStatus.REMOVED, reloaded.removeAdministrativeMember(trustedMember).status());
    }

    @Test
    void renamesLandAndPersistsTheNewName() throws Exception {
        SqliteLandStorage storage = new SqliteLandStorage(temporaryDirectory.resolve("rename.db"));
        storage.initialize();
        LandManager manager = new LandManager(storage);
        manager.load();
        UUID owner = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        manager.create(owner, "Old Name", new ChunkPosition(world, 0, 0));

        assertEquals(LandManager.RenameStatus.RENAMED, manager.rename(owner, "New Name").status());
        assertEquals("New Name", manager.ownedBy(owner).orElseThrow().name());

        LandManager reloaded = new LandManager(storage);
        reloaded.load();
        assertEquals("New Name", reloaded.ownedBy(owner).orElseThrow().name());
    }
}
