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

/** Verifies a complete snapshot against an empty or identical target without modifying the source. */
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
        var context=new MigrationContext();
        try { return migrateVerified(source,target,context); }
        catch(StorageException | RuntimeException failure) {
            String reason=String.valueOf(failure.getMessage()).replaceAll("[\\r\\n\\t]"," ")
                    .replaceAll("(?i)jdbc:[^\\s;]+","<JDBC URL omitted>")
                    .replaceAll("(?i)(password|passwd|pwd|token|secret)\\s*[:=]\\s*[^\\s;]+","$1=<redacted>");
            for(Throwable cause=failure;cause!=null;cause=cause.getCause()) if(cause instanceof java.sql.SQLException sql){
                reason+="; SQLState="+sql.getSQLState()+" vendorCode="+sql.getErrorCode()+" cause="+sql.getClass().getSimpleName();break;
            }
            throw new StorageException("phase="+context.phase+" direction="+source.type()+"->"+target.type()+"; "+reason
                    +"; backup="+(context.backup==null?"not created":context.backup.getFileName()+" ("+(context.backupVerified?"verified":"not verified")+")")
                    +"; source not modified; target="+(context.imported?"contains imported copy, not activated":"not activated"),failure);
        }
    }
    private static final class MigrationContext {
        String phase="CHECK_TYPES"; Path backup; boolean backupVerified; boolean imported;
    }
    private MigrationResult migrateVerified(LandStorage source, LandStorage target, MigrationContext context) throws StorageException {
        if (source.type().equalsIgnoreCase(target.type())) throw new StorageException("Source and target storage types must differ", null);
        context.phase="READ_SOURCE";
        StorageSnapshot sourceSnapshot = source.load();
        context.phase="VALIDATE_SOURCE";
        validate(sourceSnapshot);
        context.phase="CONNECT_TARGET";
        target.initialize();
        context.phase="INSPECT_TARGET";
        StorageSnapshot targetBefore = target.load();
        Path backup = backupDirectory.resolve("landward-" + source.type().toLowerCase(Locale.ROOT) + "-to-"
                + target.type().toLowerCase(Locale.ROOT) + "-" + BACKUP_TIME.format(LocalDateTime.now(clock)) + ".lwbak");
        context.backup=backup;
        context.phase="WRITE_BACKUP";
        backups.write(backup, sourceSnapshot);
        context.phase="VERIFY_BACKUP";
        StorageSnapshot backupSnapshot = backups.read(backup);
        if (!sameSnapshot(sourceSnapshot, backupSnapshot)) throw new StorageException("Backup verification failed: "+SnapshotDifference.describe(sourceSnapshot,backupSnapshot), null);
        context.backupVerified=true;
        context.phase="CHECK_TARGET_CONTENTS";
        if (!isEmpty(targetBefore)) {
            // A previous import may have completed but failed read-back verification.
            // Reuse it only after the same strict full comparison; never overwrite it.
            if (sameSnapshot(sourceSnapshot, targetBefore)) {
                return new MigrationResult(backup, sourceSnapshot.lands().size(), sourceSnapshot.claims().size(), sourceSnapshot.members().size());
            }
            throw new StorageException("Target contains different LandWard data; no data overwritten. "+SnapshotDifference.describe(sourceSnapshot,targetBefore), null);
        }
        context.phase="IMPORT_TARGET";
        target.importSnapshotAtomically(sourceSnapshot);
        context.imported=true;
        context.phase="READ_TARGET";
        StorageSnapshot targetAfter = target.load();
        context.phase="VERIFY_TARGET";
        if (!sameSnapshot(sourceSnapshot, targetAfter)) throw new StorageException("Target verification failed: "+SnapshotDifference.describe(sourceSnapshot,targetAfter), null);
        return new MigrationResult(backup, sourceSnapshot.lands().size(), sourceSnapshot.claims().size(), sourceSnapshot.members().size());
    }

    /** Inspects a configured target without changing it. */
    public StorageStatus inspect(LandStorage target) throws StorageException {
        target.initialize();
        StorageSnapshot snapshot = target.load();
        return new StorageStatus(target.type(), snapshot.lands().size(), snapshot.claims().size(), snapshot.members().size(), isEmpty(snapshot));
    }

    /** Clears only LandWard tables after writing and verifying a lossless backup. */
    public ClearResult clear(LandStorage target) throws StorageException {
        target.initialize();
        StorageSnapshot snapshot = target.load();
        if (isEmpty(snapshot)) return new ClearResult(null, 0, 0, 0, true);
        Path backup = backupDirectory.resolve("landward-" + target.type().toLowerCase(Locale.ROOT) + "-before-clear-"
                + BACKUP_TIME.format(LocalDateTime.now(clock)) + ".lwbak");
        backups.write(backup, snapshot);
        if (!sameSnapshot(snapshot, backups.read(backup))) throw new StorageException("The target backup could not be verified", null);
        target.clearSnapshotAtomically();
        if (!isEmpty(target.load())) throw new StorageException("Target verification failed after clearing", null);
        return new ClearResult(backup, snapshot.lands().size(), snapshot.claims().size(), snapshot.members().size(), false);
    }

    private void validate(StorageSnapshot snapshot) throws StorageException {
        io.github.devkruidenier.landward.blockprotection.ProtectionBackup.validate(snapshot);
        Set<UUID> landIds = new HashSet<>(); Set<String> names = new HashSet<>(); Set<UUID> owners = new HashSet<>();
        Set<UUID> admins = Set.copyOf(snapshot.adminLandIds());
        for (Land land : snapshot.lands()) {
            if (!landIds.add(land.id())) throw new StorageException("Source has duplicate land IDs", null);
            if (!names.add(land.name().toLowerCase(Locale.ROOT))) throw new StorageException("Source has duplicate land names", null);
            if (!admins.contains(land.id()) && !owners.add(land.ownerId())) throw new StorageException("Source has duplicate player land owners", null);
        }
        if (!landIds.containsAll(admins)) throw new StorageException("Source references an unknown administrative land", null);
        Set<UUID> featureLands = new HashSet<>();
        Set<UUID> warpIds = new HashSet<>();
        for (var features : snapshot.features()) {
            if (!landIds.contains(features.landId()) || !featureLands.add(features.landId())) throw new StorageException("Invalid feature land reference", null);
            for (var warp : features.warps()) {
                if (admins.contains(features.landId()) || !warpIds.add(warp.id()) || snapshot.claims().stream().noneMatch(
                        claim -> claim.landId().equals(features.landId()) && claim.position().equals(warp.chunk())))
                    throw new StorageException("Invalid warp destination or identity", null);
            }
        }
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

    private boolean isEmpty(StorageSnapshot snapshot) { return snapshot.lands().isEmpty() && snapshot.claims().isEmpty() && snapshot.members().isEmpty() && snapshot.adminLandIds().isEmpty() && snapshot.features().isEmpty() && snapshot.protections().isEmpty() && snapshot.placements().isEmpty(); }
    private boolean sameSnapshot(StorageSnapshot first, StorageSnapshot second) {
        return Set.copyOf(first.lands()).equals(Set.copyOf(second.lands()))
                && Set.copyOf(first.claims()).equals(Set.copyOf(second.claims()))
                && Set.copyOf(first.members()).equals(Set.copyOf(second.members()))
                && Set.copyOf(first.adminLandIds()).equals(Set.copyOf(second.adminLandIds()))
                && Set.copyOf(first.features()).equals(Set.copyOf(second.features()))
                && Set.copyOf(first.protections()).equals(Set.copyOf(second.protections()))
                && Set.copyOf(first.placements()).equals(Set.copyOf(second.placements()));
    }

    public record MigrationResult(Path backup, int lands, int claims, int members) { }
    public record StorageStatus(String type, int lands, int claims, int members, boolean empty) { }
    public record ClearResult(Path backup, int lands, int claims, int members, boolean alreadyEmpty) { }
}
