package io.github.devkruidenier.landward.storage;

import io.github.devkruidenier.landward.model.*;
import io.github.devkruidenier.landward.storage.sqlite.SqliteLandStorage;
import io.github.devkruidenier.landward.storage.migration.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FeaturePersistenceTest {
    @TempDir Path directory;
    @Test void settingsAndPreciseWarpsSurviveBackupMigrationAndOwnerChanges() throws Exception {
        var source = new SqliteLandStorage(directory.resolve("source.db")); source.initialize();
        UUID id = UUID.randomUUID(), world = UUID.randomUUID();
        var land = new Land(id, UUID.randomUUID(), "Amsterdam", Instant.now(), MemberPermissions.FULL, LandProtectionSettings.DEFAULT);
        source.insertLandWithClaim(land, new ClaimedChunk(new ChunkPosition(world, -1, -2), id));
        var features = new LandFeatures(id, EnvironmentSettings.defaults(false).with(EnvironmentSettings.Rule.FALL_DAMAGE, false),
                List.of(new LandWarp(UUID.randomUUID(), "Home", world, -0.5, 80.25, -16.5, 127.5F, -22.5F)));
        source.updateFeatures(features);
        assertEquals(List.of(features), source.load().features());
        source.updateLandOwner(id, UUID.randomUUID());
        assertEquals(List.of(features), source.load().features());
        var backup = new StorageSnapshotBackup(); var file = directory.resolve("backup.lwbak");
        backup.write(file, source.load()); assertEquals(source.load(), backup.read(file));
        var target = new SqliteLandStorage(directory.resolve("target.db")); target.initialize();
        target.importSnapshotAtomically(backup.read(file));
        assertEquals(List.of(features), target.load().features());
        target.deleteLand(id); assertTrue(target.load().features().isEmpty());
        source.clearSnapshotAtomically(); assertTrue(source.load().features().isEmpty());
    }
    @Test void readsAllOlderBackupVersionsWithoutInventingWarpsOrChangingSavedRights() throws Exception {
        for (int version = 1; version <= 3; version++) {
            var file = directory.resolve("legacy-" + version + ".lwbak"); UUID land = UUID.randomUUID(), owner = UUID.randomUUID();
            try (var output = new java.io.DataOutputStream(java.nio.file.Files.newOutputStream(file))) {
                output.writeUTF("LANDWARD-BACKUP"); output.writeInt(version); output.writeInt(1);
                for (UUID id : List.of(land, owner)) { output.writeLong(id.getMostSignificantBits()); output.writeLong(id.getLeastSignificantBits()); }
                output.writeUTF("Legacy"); output.writeLong(1000);
                if (version >= 2) { output.writeBoolean(true); output.writeBoolean(false); output.writeBoolean(true); }
                if (version >= 3) for (int i = 0; i < 6; i++) output.writeBoolean(false);
                output.writeInt(0); output.writeInt(0); output.writeInt(0);
            }
            var snapshot = new StorageSnapshotBackup().read(file);
            assertEquals(land, snapshot.lands().getFirst().id()); assertTrue(snapshot.features().isEmpty());
            if (version >= 2) assertFalse(snapshot.lands().getFirst().memberPermissions().containers());
            if (version >= 3) assertFalse(snapshot.lands().getFirst().protectionSettings().doors());
        }
    }
}
