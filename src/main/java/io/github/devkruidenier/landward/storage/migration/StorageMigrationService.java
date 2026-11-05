package io.github.devkruidenier.landward.storage.migration;

import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.model.ClaimedChunk;
import io.github.devkruidenier.landward.model.Land;
import io.github.devkruidenier.landward.model.LandMember;
import io.github.devkruidenier.landward.storage.LandStorage;
import io.github.devkruidenier.landward.storage.StorageException;
import io.github.devkruidenier.landward.storage.StorageSnapshot;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Moves a validated complete storage snapshot to an empty target without touching the active source. */
public final class StorageMigrationService {
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final Path backupDirectory;
    private final StorageSnapshotBackup backups;
    private final Clock clock;

    public StorageMigrationService(Path backupDirectory) { this(backupDirectory, new StorageSnapshotBackup(), Clock.systemUTC()); }
    StorageMigrationService(Path backupDirectory, StorageSnapshotBackup backups, Clock clock) {
        this.backupDirectory = backupDirectory; this.backups = backups; this.clock = clock;
    }

    public MigrationResult migrate(LandStorage source, LandStorage target) throws StorageException {
        if (source.type().equalsIgnoreCase(target.type())) throw new StorageException("Source and target storage types must differ", null);
        StorageSnapshot sourceSnapshot = source.load();
        validate(sourceSnapshot);
        target.initialize();
        StorageSnapshot targetBefore = target.load();
        if (!isEmpty(targetBefore)) throw new StorageException("The target storage is not empty; migration was not started", null);
        Path backup = backupDirectory.resolve("landward-" + source.type().toLowerCase(Locale.ROOT) + "-to-"
                + target.type().toLowerCase(Locale.ROOT) + "-" + BACKUP_TIME.format(LocalDateTime.now(clock)) + ".lwbak");
        backups.write(backup, sourceSnapshot);
        StorageSnapshot backupSnapshot = backups.read(backup);
        if (!sameSnapshot(sourceSnapshot, backupSnapshot)) throw new StorageException("The migration backup could not be verified", null);
        target.importSnapshotAtomically(sourceSnapshot);
        StorageSnapshot targetAfter = target.load();
        if (!sameSnapshot(sourceSnapshot, targetAfter)) throw new StorageException("Target verification failed after migration", null);
        return new MigrationResult(backup, sourceSnapshot.lands().size(), sourceSnapshot.claims().size(), sourceSnapshot.members().size());
    }

    private void validate(StorageSnapshot snapshot) throws StorageException {
        Set<UUID> landIds = new HashSet<>(); Set<String> names = new HashSet<>(); Set<UUID> owners = new HashSet<>();
        Set<UUID> admins = Set.copyOf(snapshot.adminLandIds());
        for (Land land : snapshot.lands()) {
            if (!landIds.add(land.id())) throw new StorageException("Source has duplicate land IDs", null);
            if (!names.add(land.name().toLowerCase(Locale.ROOT))) throw new StorageException("Source has duplicate land names", null);
            if (!admins.contains(land.id()) && !owners.add(land.ownerId())) throw new StorageException("Source has duplicate player land owners", null);
        }
        if (!landIds.containsAll(admins)) throw new StorageException("Source references an unknown administrative land", null);
        Set<ChunkPosition> positions = new HashSet<>();
        for (ClaimedChunk claim : snapshot.claims()) {
            if (!landIds.contains(claim.landId())) throw new StorageException("Source claim references an unknown land", null);
            if (!positions.add(claim.position())) throw new StorageException("Source has duplicate claim positions", null);
        }
        Set<String> memberKeys = new HashSet<>();
        for (LandMember member : snapshot.members()) {
            if (!landIds.contains(member.landId())) throw new StorageException("Source member references an unknown land", null);
            if (!memberKeys.add(member.landId() + ":" + member.playerId())) throw new StorageException("Source has duplicate land members", null);
        }
    }

    private boolean isEmpty(StorageSnapshot snapshot) { return snapshot.lands().isEmpty() && snapshot.claims().isEmpty() && snapshot.members().isEmpty() && snapshot.adminLandIds().isEmpty(); }
    private boolean sameSnapshot(StorageSnapshot first, StorageSnapshot second) {
        return Set.copyOf(first.lands()).equals(Set.copyOf(second.lands()))
                && Set.copyOf(first.claims()).equals(Set.copyOf(second.claims()))
                && Set.copyOf(first.members()).equals(Set.copyOf(second.members()))
                && Set.copyOf(first.adminLandIds()).equals(Set.copyOf(second.adminLandIds()));
    }

    public record MigrationResult(Path backup, int lands, int claims, int members) { }
}
