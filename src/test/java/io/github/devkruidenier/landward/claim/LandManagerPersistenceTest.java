package io.github.devkruidenier.landward.claim;

import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.model.LandType;
import io.github.devkruidenier.landward.model.MemberPermissions;
import io.github.devkruidenier.landward.storage.sqlite.SqliteLandStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
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
        assertEquals(List.of(created.land().id()), reloaded.memberLands(member).stream().map(land -> land.id()).toList());
    }

    @Test
    void copiesConfiguredMemberDefaultsWithoutOverwritingIndividualRights() throws Exception {
        SqliteLandStorage storage = new SqliteLandStorage(temporaryDirectory.resolve("member-defaults.db"));
        storage.initialize();
        MemberPermissions configuredDefaults = new MemberPermissions(false, true, false);
        LandManager manager = new LandManager(storage, configuredDefaults, MemberPermissions.NONE);
        manager.load();

        UUID owner = UUID.randomUUID();
        UUID firstMember = UUID.randomUUID();
        UUID secondMember = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        LandManager.CreateResult created = manager.create(owner, "Default Rights", new ChunkPosition(world, 0, 0));
        assertEquals(configuredDefaults, created.land().memberPermissions());
        manager.addMember(owner, firstMember);
        assertEquals(configuredDefaults, manager.permissions(firstMember, created.land().id()));

        manager.updateMemberPermission(owner, firstMember, MemberPermissions.Permission.BUILD, true);
        manager.updateAllMemberPermissions(owner, MemberPermissions.Permission.CONTAINERS, false);
        manager.addMember(owner, secondMember);

        assertEquals(new MemberPermissions(true, true, false), manager.permissions(firstMember, created.land().id()));
        assertEquals(new MemberPermissions(false, false, false), manager.permissions(secondMember, created.land().id()));
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
    void administratorCanAddAnUnclaimedChunkToAnExistingPlayerLand() throws Exception {
        SqliteLandStorage storage = new SqliteLandStorage(temporaryDirectory.resolve("admin-claim-player-land.db"));
        storage.initialize();
        LandManager manager = new LandManager(storage);
        manager.load();
        UUID owner = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        ChunkPosition initial = new ChunkPosition(world, 0, 0);
        ChunkPosition distant = new ChunkPosition(world, 12, 12);
        var land = manager.create(owner, "Admin Add", initial).land();

        assertTrue(manager.adminClaimPlayerLand(land.id(), distant));
        assertEquals(2, manager.claimCount(land.id()));
        assertEquals(land.id(), manager.landAt(distant).orElseThrow().id());
        assertTrue(!manager.adminClaimPlayerLand(land.id(), distant));
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
    void deletesEveryAdministrativeClaimAndMemberWhenSafeZonesAreRemoved() throws Exception {
        SqliteLandStorage storage = new SqliteLandStorage(temporaryDirectory.resolve("delete-safe-zones.db"));
        storage.initialize();
        LandManager manager = new LandManager(storage);
        manager.load();
        UUID world = UUID.randomUUID();
        ChunkPosition first = new ChunkPosition(world, 2, 4);
        ChunkPosition second = new ChunkPosition(world, 3, 4);
        var land = manager.claimAdministration(first).land();
        manager.claimAdministration(second);
        manager.addAdministrativeMember(UUID.randomUUID());

        assertTrue(manager.deleteAdministrativeLand().isPresent());
        assertTrue(manager.administrativeLand().isEmpty());
        assertTrue(manager.landAt(first).isEmpty());
        assertTrue(manager.landAt(second).isEmpty());
        assertTrue(manager.members(land.id()).isEmpty());

        LandManager reloaded = new LandManager(storage);
        reloaded.load();
        assertTrue(reloaded.administrativeLand().isEmpty());
        assertEquals(0, reloaded.totalClaimCount());
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

    @Test
    void administratorCanTransferAPlayerLandToAnOfflineUuidWithoutCreatingSecondOwnership() throws Exception {
        SqliteLandStorage storage = new SqliteLandStorage(temporaryDirectory.resolve("transfer-owner.db"));
        storage.initialize();
        LandManager manager = new LandManager(storage);
        manager.load();
        UUID firstOwner = UUID.randomUUID();
        UUID newOwner = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        var firstLand = manager.create(firstOwner, "Transfer One", new ChunkPosition(world, 0, 0)).land();
        manager.addMember(firstOwner, newOwner);

        assertEquals(LandManager.TransferStatus.TRANSFERRED, manager.transferOwnership(firstLand.id(), newOwner).status());
        assertTrue(manager.ownedBy(firstOwner).isEmpty());
        assertEquals(firstLand.id(), manager.ownedBy(newOwner).orElseThrow().id());
        assertTrue(manager.members(firstLand.id()).isEmpty(), "The new owner must not retain a redundant member record");

        LandManager reloaded = new LandManager(storage);
        reloaded.load();
        assertEquals(firstLand.id(), reloaded.ownedBy(newOwner).orElseThrow().id());
        UUID secondOwner = UUID.randomUUID();
        var secondLand = reloaded.create(secondOwner, "Transfer Two", new ChunkPosition(world, 4, 0)).land();
        assertEquals(LandManager.TransferStatus.NEW_OWNER_ALREADY_OWNS_LAND,
                reloaded.transferOwnership(secondLand.id(), newOwner).status());
    }
}
