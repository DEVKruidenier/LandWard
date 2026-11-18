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
        manager.addWarp(owner, land.id(), new io.github.devkruidenier.landward.model.LandWarp(UUID.randomUUID(),
                "Home", world, 18.25, 80.5, 33.75, 137.5F, -22.5F), 3);
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
    void resumesAnExactlyMatchingImportedTargetWithoutWritingItAgain() throws Exception {
        try (var source=new SqliteLandStorage(directory.resolve("resume-source.db"));
             var target=new SqliteLandStorage(directory.resolve("resume-target.db"))) {
            source.initialize(); target.initialize();
            var manager=new LandManager(source); manager.load();
            var owner=UUID.randomUUID(); var world=UUID.randomUUID();
            var land=manager.create(owner,"Resume",new ChunkPosition(world,0,0)).land();
            manager.addWarp(owner,land.id(),new io.github.devkruidenier.landward.model.LandWarp(UUID.randomUUID(),
                    "Home",world,.125,80,.875,137.54298F,-22.513847F),3);
            var expected=source.load(); target.importSnapshotAtomically(expected);
            var readOnlyTarget=new TypedStorage(target,"MYSQL") {
                @Override public void importSnapshotAtomically(StorageSnapshot snapshot) { throw new AssertionError("Matching target must not be reimported"); }
            };
            var result=new StorageMigrationService(directory.resolve("resume-backups")).migrate(source,readOnlyTarget);
            assertEquals(expected,target.load()); assertEquals(expected,source.load());
            assertEquals(expected,new StorageSnapshotBackup().read(result.backup()));
            target.updateLandName(land.id(),"Changed target");
            var different=target.load();
            assertThrows(StorageException.class,()->new StorageMigrationService(directory.resolve("reject-backups")).migrate(source,readOnlyTarget));
            assertEquals(different,target.load()); assertEquals(expected,source.load());
        }
    }

    @Test
    void verificationFailureIdentifiesWarpFieldValuesAndVerifiedBackup() throws Exception {
        try(var source=new SqliteLandStorage(directory.resolve("diagnostic-source.db"));
            var sqlTarget=new SqliteLandStorage(directory.resolve("diagnostic-target.db"))) {
            source.initialize(); var manager=new LandManager(source); manager.load();
            UUID owner=UUID.randomUUID(),world=UUID.randomUUID(),warpId=UUID.randomUUID();
            var land=manager.create(owner,"Diagnostic",new ChunkPosition(world,0,0)).land();
            manager.addWarp(owner,land.id(),new io.github.devkruidenier.landward.model.LandWarp(warpId,
                    "Home",world,.5,80,.5,137.54298F,-22.513847F),3);
            var expected=source.load();
            var broken=new TypedStorage(sqlTarget,"MYSQL") {
                @Override public void importSnapshotAtomically(StorageSnapshot snapshot) throws StorageException {
                    sqlTarget.importSnapshotAtomically(snapshot);
                    var features=snapshot.features().getFirst();var warp=features.warps().getFirst();
                    sqlTarget.updateFeatures(new io.github.devkruidenier.landward.model.LandFeatures(features.landId(),features.environment(),
                            java.util.List.of(new io.github.devkruidenier.landward.model.LandWarp(warp.id(),warp.name(),warp.worldId(),warp.x(),warp.y(),warp.z(),137.543F,warp.pitch()))));
                }
            };
            String message=assertThrows(StorageException.class,()->new StorageMigrationService(directory.resolve("diagnostic-backups")).migrate(source,broken)).getMessage();
            org.junit.jupiter.api.Assertions.assertTrue(message.contains("phase=VERIFY_TARGET"),message);
            org.junit.jupiter.api.Assertions.assertTrue(message.contains("warp="+warpId)&&message.contains("field=yaw"),message);
            org.junit.jupiter.api.Assertions.assertTrue(message.contains("source=137.54298")&&message.contains("target=137.543"),message);
            org.junit.jupiter.api.Assertions.assertTrue(message.contains("(verified)")&&message.contains("source not modified"),message);
            assertEquals(expected,source.load());
        }
    }

    @Test
    void connectionFailureNamesThePhaseAndSqlStateWithoutLoggingDriverSecrets() throws Exception {
        try(var source=new SqliteLandStorage(directory.resolve("connect-source.db"));
            var sqlTarget=new SqliteLandStorage(directory.resolve("connect-target.db"))) {
            source.initialize();
            var failing=new TypedStorage(sqlTarget,"MYSQL") {
                @Override public void initialize() throws StorageException {
                    throw new StorageException("Connection failed",new java.sql.SQLException("password=hidden-driver-secret jdbc:mysql://private", "28000",1045));
                }
            };
            String message=assertThrows(StorageException.class,()->new StorageMigrationService(directory.resolve("connect-backups")).migrate(source,failing)).getMessage();
            org.junit.jupiter.api.Assertions.assertTrue(message.contains("phase=CONNECT_TARGET")&&message.contains("SQLState=28000")&&message.contains("vendorCode=1045"),message);
            assertFalse(message.contains("hidden-driver-secret"));assertFalse(message.contains("jdbc:mysql"));
            org.junit.jupiter.api.Assertions.assertTrue(message.contains("backup=not created"),message);
        }
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

    private static class TypedStorage implements LandStorage {
        private final LandStorage delegate;
        private final String type;
        TypedStorage(LandStorage delegate,String type){this.delegate=delegate;this.type=type;}
        @Override public String type(){return type;}
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
