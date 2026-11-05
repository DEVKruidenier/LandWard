package io.github.devkruidenier.landward.storage.migration;

import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.model.ClaimedChunk;
import io.github.devkruidenier.landward.model.Land;
import io.github.devkruidenier.landward.model.LandMember;
import io.github.devkruidenier.landward.model.MemberPermissions;
import io.github.devkruidenier.landward.storage.StorageException;
import io.github.devkruidenier.landward.storage.StorageSnapshot;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Versioned, lossless backup format used before every storage migration. */
public final class StorageSnapshotBackup {
    private static final String MAGIC = "LANDWARD-BACKUP";
    private static final int VERSION = 1;

    public void write(Path file, StorageSnapshot snapshot) throws StorageException {
        try {
            Files.createDirectories(file.getParent());
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
                output.writeUTF(MAGIC);
                output.writeInt(VERSION);
                writeLands(output, snapshot.lands());
                writeClaims(output, snapshot.claims());
                writeMembers(output, snapshot.members());
                output.writeInt(snapshot.adminLandIds().size());
                for (UUID id : snapshot.adminLandIds()) writeUuid(output, id);
            }
        } catch (IOException exception) { throw new StorageException("Could not write migration backup", exception); }
    }

    public StorageSnapshot read(Path file) throws StorageException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (!MAGIC.equals(input.readUTF())) throw new StorageException("Not a LandWard migration backup", null);
            if (input.readInt() != VERSION) throw new StorageException("Unsupported LandWard migration backup version", null);
            List<Land> lands = readLands(input);
            List<ClaimedChunk> claims = readClaims(input);
            List<LandMember> members = readMembers(input);
            int adminCount = count(input);
            List<UUID> adminIds = new ArrayList<>(adminCount);
            for (int index = 0; index < adminCount; index++) adminIds.add(readUuid(input));
            if (input.read() != -1) throw new StorageException("Migration backup has trailing data", null);
            return new StorageSnapshot(List.copyOf(lands), List.copyOf(claims), List.copyOf(members), List.copyOf(adminIds));
        } catch (EOFException exception) { throw new StorageException("Migration backup is incomplete", exception); }
        catch (IOException exception) { throw new StorageException("Could not read migration backup", exception); }
    }

    private void writeLands(DataOutputStream output, List<Land> lands) throws IOException {
        output.writeInt(lands.size());
        for (Land land : lands) { writeUuid(output, land.id()); writeUuid(output, land.ownerId()); output.writeUTF(land.name()); output.writeLong(land.createdAt().toEpochMilli()); }
    }
    private List<Land> readLands(DataInputStream input) throws IOException, StorageException {
        int size = count(input); List<Land> lands = new ArrayList<>(size);
        for (int index = 0; index < size; index++) lands.add(new Land(readUuid(input), readUuid(input), input.readUTF(), Instant.ofEpochMilli(input.readLong())));
        return lands;
    }
    private void writeClaims(DataOutputStream output, List<ClaimedChunk> claims) throws IOException {
        output.writeInt(claims.size());
        for (ClaimedChunk claim : claims) { writeUuid(output, claim.position().worldId()); output.writeInt(claim.position().x()); output.writeInt(claim.position().z()); writeUuid(output, claim.landId()); }
    }
    private List<ClaimedChunk> readClaims(DataInputStream input) throws IOException, StorageException {
        int size = count(input); List<ClaimedChunk> claims = new ArrayList<>(size);
        for (int index = 0; index < size; index++) claims.add(new ClaimedChunk(new ChunkPosition(readUuid(input), input.readInt(), input.readInt()), readUuid(input)));
        return claims;
    }
    private void writeMembers(DataOutputStream output, List<LandMember> members) throws IOException {
        output.writeInt(members.size());
        for (LandMember member : members) { writeUuid(output, member.landId()); writeUuid(output, member.playerId()); output.writeBoolean(member.permissions().build()); output.writeBoolean(member.permissions().containers()); output.writeBoolean(member.permissions().entities()); }
    }
    private List<LandMember> readMembers(DataInputStream input) throws IOException, StorageException {
        int size = count(input); List<LandMember> members = new ArrayList<>(size);
        for (int index = 0; index < size; index++) members.add(new LandMember(readUuid(input), readUuid(input), new MemberPermissions(input.readBoolean(), input.readBoolean(), input.readBoolean())));
        return members;
    }
    private int count(DataInputStream input) throws IOException, StorageException {
        int value = input.readInt();
        if (value < 0 || value > 1_000_000) throw new StorageException("Migration backup contains an invalid entry count", null);
        return value;
    }
    private void writeUuid(DataOutputStream output, UUID id) throws IOException { output.writeLong(id.getMostSignificantBits()); output.writeLong(id.getLeastSignificantBits()); }
    private UUID readUuid(DataInputStream input) throws IOException { return new UUID(input.readLong(), input.readLong()); }
}
