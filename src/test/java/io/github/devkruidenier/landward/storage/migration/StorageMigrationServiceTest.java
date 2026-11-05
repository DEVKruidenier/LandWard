package io.github.devkruidenier.landward.storage.migration;

import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.model.ClaimedChunk;
import io.github.devkruidenier.landward.model.Land;
import io.github.devkruidenier.landward.model.LandMember;
import io.github.devkruidenier.landward.model.MemberPermissions;
import io.github.devkruidenier.landward.model.LandProtectionSettings;
import io.github.devkruidenier.landward.storage.LandStorage;
import io.github.devkruidenier.landward.storage.StorageException;
import io.github.devkruidenier.landward.storage.StorageSnapshot;
import io.github.devkruidenier.landward.storage.sqlite.SqliteLandStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StorageMigrationServiceTest {
    @TempDir Path directory;

    @Test
    void createsVerifiedBackupAndMigratesLandsClaimsMembersAndAdminClaims() throws Exception {
        SqliteLandStorage sourceSqlite = new SqliteLandStorage(directory.resolve("source.db"));
        sourceSqlite.initialize();
        LandManager manager = new LandManager(sourceSqlite);
        manager.load();
        UUID world = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        var land = manager.create(owner, "Migration Home", new ChunkPosition(world, 1, 2)).land();
        manager.claim(owner, new ChunkPosition(world, 2, 2), -1, true);
        manager.addMember(owner, member);
        manager.claimAdministration("Safe Zone", new ChunkPosition(world, 9, 9));

        SqliteLandStorage targetSqlite = new SqliteLandStorage(directory.resolve("target.db"));
        StorageMigrationService service = new StorageMigrationService(directory.resolve("backups"), new StorageSnapshotBackup(),
                Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC));
        var result = service.migrate(new TypedStorage(sourceSqlite, "SQLITE"), new TypedStorage(targetSqlite, "MYSQL"));

        assertEquals(2, result.lands());
        assertEquals(3, result.claims());
        assertEquals(1, result.members());
        assertEquals(sourceSqlite.load(), targetSqlite.load());
        assertFalse(Files.size(result.backup()) == 0);
        assertEquals(sourceSqlite.load(), new StorageSnapshotBackup().read(result.backup()));
        assertEquals(land.id(), targetSqlite.load().claims().stream().filter(claim -> claim.position().x() == 1).findFirst().orElseThrow().landId());
    }

    @Test
    void refusesToOverwriteANonEmptyTarget() throws Exception {
        SqliteLandStorage source = new SqliteLandStorage(directory.resolve("source.db")); source.initialize();
        SqliteLandStorage target = new SqliteLandStorage(directory.resolve("target.db")); target.initialize();
        LandManager sourceManager = new LandManager(source); sourceManager.load();
        LandManager targetManager = new LandManager(target); targetManager.load();
        UUID world = UUID.randomUUID();
        sourceManager.create(UUID.randomUUID(), "Source Land", new ChunkPosition(world, 0, 0));
        targetManager.create(UUID.randomUUID(), "Target Land", new ChunkPosition(world, 4, 0));

        StorageMigrationService service = new StorageMigrationService(directory.resolve("backups"));
        assertThrows(StorageException.class, () -> service.migrate(new TypedStorage(source, "SQLITE"), new TypedStorage(target, "MYSQL")));
        assertEquals(1, target.load().lands().size());
    }

    private record TypedStorage(LandStorage delegate, String type) implements LandStorage {
        @Override public void initialize() throws StorageException { delegate.initialize(); }
        @Override public StorageSnapshot load() throws StorageException { return delegate.load(); }
        @Override public void importSnapshotAtomically(StorageSnapshot snapshot) throws StorageException { delegate.importSnapshotAtomically(snapshot); }
        @Override public void clearSnapshotAtomically() throws StorageException { delegate.clearSnapshotAtomically(); }
        @Override public void insertLand(Land land) throws StorageException { delegate.insertLand(land); }
        @Override public void insertLandWithClaim(Land land, ClaimedChunk claim) throws StorageException { delegate.insertLandWithClaim(land, claim); }
        @Override public void insertAdminLandWithClaim(Land land, ClaimedChunk claim) throws StorageException { delegate.insertAdminLandWithClaim(land, claim); }
        @Override public void updateLandName(UUID landId, String name) throws StorageException { delegate.updateLandName(landId, name); }
        @Override public void updateLandOwner(UUID landId, UUID ownerId) throws StorageException { delegate.updateLandOwner(landId, ownerId); }
        @Override public void updateLandMemberPermissions(UUID landId, MemberPermissions permissions) throws StorageException { delegate.updateLandMemberPermissions(landId, permissions); }
        @Override public void updateAllMemberPermissions(UUID landId, MemberPermissions permissions) throws StorageException { delegate.updateAllMemberPermissions(landId, permissions); }
        @Override public void updateLandProtectionSettings(UUID landId, LandProtectionSettings settings) throws StorageException { delegate.updateLandProtectionSettings(landId, settings); }
        @Override public void insertClaim(ClaimedChunk claim) throws StorageException { delegate.insertClaim(claim); }
        @Override public void deleteClaim(ClaimedChunk claim) throws StorageException { delegate.deleteClaim(claim); }
        @Override public void deleteLand(UUID landId) throws StorageException { delegate.deleteLand(landId); }
        @Override public void insertMember(LandMember member) throws StorageException { delegate.insertMember(member); }
        @Override public void updateMember(LandMember member) throws StorageException { delegate.updateMember(member); }
        @Override public void deleteMember(UUID landId, UUID playerId) throws StorageException { delegate.deleteMember(landId, playerId); }
        @Override public void close() throws StorageException { delegate.close(); }
    }
}
