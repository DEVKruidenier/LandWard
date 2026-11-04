package io.github.devkruidenier.landward.claim;

import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.model.ClaimedChunk;
import io.github.devkruidenier.landward.model.Land;
import io.github.devkruidenier.landward.model.LandMember;
import io.github.devkruidenier.landward.model.MemberPermissions;
import io.github.devkruidenier.landward.storage.LandStorage;
import io.github.devkruidenier.landward.storage.StorageException;
import io.github.devkruidenier.landward.storage.StorageSnapshot;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public final class LandManager {

    private static final Pattern VALID_NAME = Pattern.compile("^[\\p{L}\\p{N} _-]{3,24}$");

    private final LandStorage storage;
    private final Map<UUID, Land> landsById = new HashMap<>();
    private final Map<UUID, Land> landsByOwner = new HashMap<>();
    private final Map<String, Land> landsByName = new HashMap<>();
    private final Map<ChunkPosition, UUID> claims = new HashMap<>();
    private final Map<UUID, Integer> claimCounts = new HashMap<>();
    private final Map<UUID, Map<UUID, MemberPermissions>> membersByLand = new HashMap<>();

    public LandManager(LandStorage storage) {
        this.storage = storage;
    }

    public synchronized void load() throws StorageException {
        StorageSnapshot snapshot = storage.load();
        landsById.clear();
        landsByOwner.clear();
        landsByName.clear();
        claims.clear();
        claimCounts.clear();
        membersByLand.clear();

        for (Land land : snapshot.lands()) {
            landsById.put(land.id(), land);
            landsByOwner.put(land.ownerId(), land);
            landsByName.put(normalizedName(land.name()), land);
        }
        for (ClaimedChunk claim : snapshot.claims()) {
            if (!landsById.containsKey(claim.landId())) {
                throw new StorageException("Claim references an unknown land: " + claim.landId(), null);
            }
            claims.put(claim.position(), claim.landId());
            claimCounts.merge(claim.landId(), 1, Integer::sum);
        }
        for (LandMember member : snapshot.members()) {
            if (!landsById.containsKey(member.landId())) {
                throw new StorageException("Member references an unknown land: " + member.landId(), null);
            }
            membersByLand.computeIfAbsent(member.landId(), ignored -> new HashMap<>())
                    .put(member.playerId(), member.permissions());
        }
    }

    public synchronized CreateResult create(
            UUID ownerId,
            String requestedName,
            ChunkPosition initialPosition
    ) throws StorageException {
        String name = requestedName.strip();
        if (!VALID_NAME.matcher(name).matches()) {
            return new CreateResult(CreateStatus.INVALID_NAME, null);
        }
        if (landsByOwner.containsKey(ownerId)) {
            return new CreateResult(CreateStatus.ALREADY_OWNER, landsByOwner.get(ownerId));
        }
        if (landsByName.containsKey(normalizedName(name))) {
            return new CreateResult(CreateStatus.NAME_TAKEN, landsByName.get(normalizedName(name)));
        }
        UUID claimedBy = claims.get(initialPosition);
        if (claimedBy != null) {
            return new CreateResult(CreateStatus.CHUNK_ALREADY_CLAIMED, landsById.get(claimedBy));
        }

        Land land = new Land(UUID.randomUUID(), ownerId, name, Instant.now());
        storage.insertLandWithClaim(land, new ClaimedChunk(initialPosition, land.id()));
        landsById.put(land.id(), land);
        landsByOwner.put(ownerId, land);
        landsByName.put(normalizedName(name), land);
        claims.put(initialPosition, land.id());
        claimCounts.put(land.id(), 1);
        return new CreateResult(CreateStatus.CREATED, land);
    }

    public synchronized ClaimResult claim(
            UUID ownerId,
            ChunkPosition position,
            int maximumChunks,
            boolean requireAdjacent
    ) throws StorageException {
        Land land = landsByOwner.get(ownerId);
        if (land == null) {
            return new ClaimResult(ClaimStatus.NO_LAND, null, 0);
        }
        UUID existingLandId = claims.get(position);
        if (existingLandId != null) {
            return new ClaimResult(
                    existingLandId.equals(land.id()) ? ClaimStatus.ALREADY_YOURS : ClaimStatus.ALREADY_CLAIMED,
                    landsById.get(existingLandId),
                    claimCount(land.id())
            );
        }

        int currentCount = claimCount(land.id());
        if (maximumChunks >= 0 && currentCount >= maximumChunks) {
            return new ClaimResult(ClaimStatus.LIMIT_REACHED, land, currentCount);
        }
        if (requireAdjacent && currentCount > 0 && !isAdjacent(position, land.id())) {
            return new ClaimResult(ClaimStatus.NOT_ADJACENT, land, currentCount);
        }

        storage.insertClaim(new ClaimedChunk(position, land.id()));
        claims.put(position, land.id());
        claimCounts.put(land.id(), currentCount + 1);
        return new ClaimResult(ClaimStatus.CLAIMED, land, currentCount + 1);
    }

    public synchronized UnclaimResult unclaim(UUID ownerId, ChunkPosition position) throws StorageException {
        Land land = landsByOwner.get(ownerId);
        if (land == null) {
            return new UnclaimResult(UnclaimStatus.NO_LAND, null, 0);
        }
        UUID claimOwner = claims.get(position);
        if (claimOwner == null) {
            return new UnclaimResult(UnclaimStatus.UNCLAIMED, null, claimCount(land.id()));
        }
        if (!claimOwner.equals(land.id())) {
            return new UnclaimResult(UnclaimStatus.NOT_YOURS, landsById.get(claimOwner), claimCount(land.id()));
        }
        int count = claimCount(land.id());
        if (count <= 1) {
            return new UnclaimResult(UnclaimStatus.LAST_CHUNK, land, count);
        }
        storage.deleteClaim(new ClaimedChunk(position, land.id()));
        claims.remove(position);
        claimCounts.put(land.id(), count - 1);
        return new UnclaimResult(UnclaimStatus.UNCLAIMED_SUCCESS, land, count - 1);
    }

    public synchronized Optional<Land> landAt(ChunkPosition position) {
        return Optional.ofNullable(claims.get(position)).map(landsById::get);
    }

    public synchronized Optional<Land> ownedBy(UUID ownerId) {
        return Optional.ofNullable(landsByOwner.get(ownerId));
    }

    public synchronized int claimCount(UUID landId) {
        return claimCounts.getOrDefault(landId, 0);
    }

    public synchronized int totalClaimCount() {
        return claims.size();
    }

    public synchronized List<LandMember> members(UUID landId) {
        return membersByLand.getOrDefault(landId, Map.of()).entrySet().stream()
                .map(entry -> new LandMember(landId, entry.getKey(), entry.getValue()))
                .toList();
    }

    public synchronized MemberPermissions permissions(UUID playerId, UUID landId) {
        Land land = landsById.get(landId);
        if (land != null && land.ownerId().equals(playerId)) {
            return MemberPermissions.FULL;
        }
        return membersByLand.getOrDefault(landId, Map.of()).getOrDefault(playerId, MemberPermissions.NONE);
    }

    public synchronized MemberResult addMember(UUID ownerId, UUID memberId) throws StorageException {
        Land land = landsByOwner.get(ownerId);
        if (land == null) {
            return new MemberResult(MemberStatus.NO_LAND, null, MemberPermissions.NONE);
        }
        if (ownerId.equals(memberId)) {
            return new MemberResult(MemberStatus.CANNOT_ADD_OWNER, land, MemberPermissions.FULL);
        }
        Map<UUID, MemberPermissions> members = membersByLand.computeIfAbsent(land.id(), ignored -> new HashMap<>());
        if (members.containsKey(memberId)) {
            return new MemberResult(MemberStatus.ALREADY_MEMBER, land, members.get(memberId));
        }
        LandMember member = new LandMember(land.id(), memberId, MemberPermissions.FULL);
        storage.insertMember(member);
        members.put(memberId, member.permissions());
        return new MemberResult(MemberStatus.ADDED, land, member.permissions());
    }

    public synchronized MemberResult removeMember(UUID ownerId, UUID memberId) throws StorageException {
        Land land = landsByOwner.get(ownerId);
        if (land == null) {
            return new MemberResult(MemberStatus.NO_LAND, null, MemberPermissions.NONE);
        }
        Map<UUID, MemberPermissions> members = membersByLand.getOrDefault(land.id(), Map.of());
        if (!members.containsKey(memberId)) {
            return new MemberResult(MemberStatus.NOT_MEMBER, land, MemberPermissions.NONE);
        }
        storage.deleteMember(land.id(), memberId);
        membersByLand.get(land.id()).remove(memberId);
        return new MemberResult(MemberStatus.REMOVED, land, MemberPermissions.NONE);
    }

    public synchronized MemberResult updateMemberPermission(
            UUID ownerId,
            UUID memberId,
            MemberPermissions.Permission permission,
            boolean enabled
    ) throws StorageException {
        Land land = landsByOwner.get(ownerId);
        if (land == null) {
            return new MemberResult(MemberStatus.NO_LAND, null, MemberPermissions.NONE);
        }
        Map<UUID, MemberPermissions> members = membersByLand.getOrDefault(land.id(), Map.of());
        MemberPermissions current = members.get(memberId);
        if (current == null) {
            return new MemberResult(MemberStatus.NOT_MEMBER, land, MemberPermissions.NONE);
        }
        MemberPermissions updated = current.with(permission, enabled);
        storage.updateMember(new LandMember(land.id(), memberId, updated));
        membersByLand.get(land.id()).put(memberId, updated);
        return new MemberResult(MemberStatus.PERMISSION_UPDATED, land, updated);
    }

    private boolean isAdjacent(ChunkPosition position, UUID landId) {
        return belongsTo(position.worldId(), position.x() + 1, position.z(), landId)
                || belongsTo(position.worldId(), position.x() - 1, position.z(), landId)
                || belongsTo(position.worldId(), position.x(), position.z() + 1, landId)
                || belongsTo(position.worldId(), position.x(), position.z() - 1, landId);
    }

    private boolean belongsTo(UUID worldId, int x, int z, UUID landId) {
        return landId.equals(claims.get(new ChunkPosition(worldId, x, z)));
    }

    private String normalizedName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public record CreateResult(CreateStatus status, Land land) {
    }

    public enum CreateStatus {
        CREATED,
        INVALID_NAME,
        ALREADY_OWNER,
        NAME_TAKEN,
        CHUNK_ALREADY_CLAIMED
    }

    public record ClaimResult(ClaimStatus status, Land land, int claimCount) {
    }

    public enum ClaimStatus {
        CLAIMED,
        NO_LAND,
        ALREADY_YOURS,
        ALREADY_CLAIMED,
        LIMIT_REACHED,
        NOT_ADJACENT
    }

    public record UnclaimResult(UnclaimStatus status, Land land, int claimCount) {
    }

    public enum UnclaimStatus {
        UNCLAIMED_SUCCESS,
        NO_LAND,
        UNCLAIMED,
        NOT_YOURS,
        LAST_CHUNK
    }

    public record MemberResult(MemberStatus status, Land land, MemberPermissions permissions) {
    }

    public enum MemberStatus {
        ADDED,
        REMOVED,
        PERMISSION_UPDATED,
        NO_LAND,
        CANNOT_ADD_OWNER,
        ALREADY_MEMBER,
        NOT_MEMBER
    }
}
