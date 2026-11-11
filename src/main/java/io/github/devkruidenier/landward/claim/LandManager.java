package io.github.devkruidenier.landward.claim;

import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.model.ClaimedChunk;
import io.github.devkruidenier.landward.model.Land;
import io.github.devkruidenier.landward.model.LandMember;
import io.github.devkruidenier.landward.model.LandType;
import io.github.devkruidenier.landward.model.LandProtectionSettings;
import io.github.devkruidenier.landward.model.MemberPermissions;
import io.github.devkruidenier.landward.storage.LandStorage;
import io.github.devkruidenier.landward.storage.StorageException;
import io.github.devkruidenier.landward.storage.StorageSnapshot;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public final class LandManager {

    private static final Pattern VALID_NAME = Pattern.compile("^[\\p{L}\\p{N} _-]{3,24}$");

    private LandStorage storage;
    /** Defaults copied into a land when it is first created. Existing lands keep their own choices. */
    private MemberPermissions playerMemberDefaults;
    private MemberPermissions administrativeMemberDefaults;
    private final Map<UUID, Land> landsById = new HashMap<>();
    private final Map<UUID, Land> landsByOwner = new HashMap<>();
    private final Map<String, Land> landsByName = new HashMap<>();
    private final Map<ChunkPosition, UUID> claims = new HashMap<>();
    private final Map<UUID, Integer> claimCounts = new HashMap<>();
    private final Map<UUID, Map<UUID, MemberPermissions>> membersByLand = new HashMap<>();
    private final java.util.Set<UUID> administrativeLandIds = new HashSet<>();

    /**
     * Keeps storage and migration callers backwards-compatible. The plugin
     * constructor supplies configured defaults; direct callers receive the
     * historic full-access defaults.
     */
    public LandManager(LandStorage storage) {
        this(storage, MemberPermissions.FULL, MemberPermissions.FULL);
    }

    public LandManager(LandStorage storage, MemberPermissions playerMemberDefaults, MemberPermissions administrativeMemberDefaults) {
        this.storage = storage;
        this.playerMemberDefaults = java.util.Objects.requireNonNull(playerMemberDefaults, "playerMemberDefaults");
        this.administrativeMemberDefaults = java.util.Objects.requireNonNull(administrativeMemberDefaults, "administrativeMemberDefaults");
    }

    /** Updates creation defaults only; already-created lands and members are never changed. */
    public synchronized void memberDefaults(MemberPermissions playerDefaults, MemberPermissions adminDefaults) {
        this.playerMemberDefaults = java.util.Objects.requireNonNull(playerDefaults, "playerDefaults");
        this.administrativeMemberDefaults = java.util.Objects.requireNonNull(adminDefaults, "adminDefaults");
    }

    public synchronized void load() throws StorageException {
        StorageSnapshot snapshot = storage.load();
        landsById.clear();
        landsByOwner.clear();
        landsByName.clear();
        claims.clear();
        claimCounts.clear();
        membersByLand.clear();
        administrativeLandIds.clear();
        administrativeLandIds.addAll(snapshot.adminLandIds());

        for (Land land : snapshot.lands()) {
            landsById.put(land.id(), land);
            if (!administrativeLandIds.contains(land.id())) landsByOwner.put(land.ownerId(), land);
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

    /** Switches the durable backend while keeping the already verified in-memory snapshot. */
    public synchronized void replaceStorage(LandStorage replacement) {
        this.storage = java.util.Objects.requireNonNull(replacement, "replacement");
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

        Land land = new Land(UUID.randomUUID(), ownerId, name, Instant.now(), playerMemberDefaults, LandProtectionSettings.DEFAULT);
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

    /**
     * Adds an unclaimed chunk to one existing player land for ClaimAdmin.
     * This deliberately bypasses player limits, adjacency checks and payments.
     */
    public synchronized boolean adminClaimPlayerLand(UUID landId, ChunkPosition position) throws StorageException {
        Land land = landsById.get(landId);
        if (land == null || isAdministrative(land) || claims.containsKey(position)) return false;
        storage.insertClaim(new ClaimedChunk(position, land.id()));
        claims.put(position, land.id());
        claimCounts.merge(land.id(), 1, Integer::sum);
        return true;
    }

    /**
     * Adds a chunk to the single server-owned administrative land. Administrative
     * claims deliberately have no adjacency, per-player limit or player payment.
     */
    public synchronized AdminClaimResult claimAdministration(ChunkPosition position) throws StorageException {
        return claimAdministration("Safe Zone", position);
    }

    /** Adds a chunk to a server-owned administrative land, creating it when needed. */
    public synchronized AdminClaimResult claimAdministration(String requestedName, ChunkPosition position) throws StorageException {
        UUID existing = claims.get(position);
        if (existing != null) return new AdminClaimResult(AdminClaimStatus.ALREADY_CLAIMED, landsById.get(existing));

        Land administrativeLand = administrativeLandIds.stream()
                .map(landsById::get)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (administrativeLand == null) {
            String name = nextAdministrativeName(requestedName);
            administrativeLand = new Land(UUID.randomUUID(), UUID.randomUUID(), name, Instant.now(), administrativeMemberDefaults, LandProtectionSettings.DEFAULT);
            storage.insertAdminLandWithClaim(administrativeLand, new ClaimedChunk(position, administrativeLand.id()));
            landsById.put(administrativeLand.id(), administrativeLand);
            landsByName.put(normalizedName(administrativeLand.name()), administrativeLand);
            administrativeLandIds.add(administrativeLand.id());
            claims.put(position, administrativeLand.id());
            claimCounts.put(administrativeLand.id(), 1);
            return new AdminClaimResult(AdminClaimStatus.CREATED, administrativeLand);
        }
        storage.insertClaim(new ClaimedChunk(position, administrativeLand.id()));
        claims.put(position, administrativeLand.id());
        claimCounts.merge(administrativeLand.id(), 1, Integer::sum);
        return new AdminClaimResult(AdminClaimStatus.CLAIMED, administrativeLand);
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

    /** Deletes a player's complete land, including its final claim and all member rights. */
    public synchronized Optional<Land> deleteOwnedLand(UUID ownerId) throws StorageException {
        Land land = landsByOwner.get(ownerId);
        if (land == null) return Optional.empty();
        deleteLand(land);
        return Optional.of(land);
    }

    /** Deletes the complete server-owned Safe Zone land and every administrative chunk in it. */
    public synchronized Optional<Land> deleteAdministrativeLand() throws StorageException {
        Land land = administrativeLand().orElse(null);
        if (land == null) return Optional.empty();
        deleteLand(land);
        return Optional.of(land);
    }

    private void deleteLand(Land land) throws StorageException {
        storage.deleteLand(land.id());
        claims.entrySet().removeIf(entry -> entry.getValue().equals(land.id()));
        claimCounts.remove(land.id());
        landsById.remove(land.id());
        landsByOwner.remove(land.ownerId());
        landsByName.remove(normalizedName(land.name()));
        membersByLand.remove(land.id());
        administrativeLandIds.remove(land.id());
    }

    /** Removes a claim regardless of ownership. Intended only for the protected administrator GUI. */
    public synchronized AdminUnclaimResult adminUnclaim(ChunkPosition position) throws StorageException {
        UUID landId = claims.get(position);
        if (landId == null) return new AdminUnclaimResult(AdminUnclaimStatus.UNCLAIMED, null, 0);
        Land land = landsById.get(landId);
        int count = claimCount(landId);
        if (count <= 1) {
            deleteLand(land);
            return new AdminUnclaimResult(AdminUnclaimStatus.LAND_DELETED, land, 0);
        }
        storage.deleteClaim(new ClaimedChunk(position, landId));
        claims.remove(position);
        claimCounts.put(landId, count - 1);
        return new AdminUnclaimResult(AdminUnclaimStatus.CLAIM_REMOVED, land, count - 1);
    }

    public synchronized Optional<Land> landAt(ChunkPosition position) {
        return Optional.ofNullable(claims.get(position)).map(landsById::get);
    }

    public synchronized Optional<Land> ownedBy(UUID ownerId) {
        return Optional.ofNullable(landsByOwner.get(ownerId));
    }

    /** Returns all player-owned lands for administrative review; server claims are excluded. */
    public synchronized List<Land> playerLands() {
        return landsById.values().stream()
                .filter(land -> !administrativeLandIds.contains(land.id()))
                .sorted(java.util.Comparator.comparing(Land::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Returns every player land where this UUID is a trusted member, never server-owned safe zones. */
    public synchronized List<Land> memberLands(UUID playerId) {
        return landsById.values().stream()
                .filter(land -> !administrativeLandIds.contains(land.id()))
                .filter(land -> membersByLand.getOrDefault(land.id(), Map.of()).containsKey(playerId))
                .sorted(java.util.Comparator.comparing(Land::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Transfers a player land to a UUID. The destination may be offline, but
     * can never own a second player land. Existing membership remains intact,
     * except the new owner's redundant member entry is removed.
     */
    public synchronized TransferResult transferOwnership(UUID landId, UUID newOwnerId) throws StorageException {
        Land current = landsById.get(landId);
        if (current == null || administrativeLandIds.contains(landId)) return new TransferResult(TransferStatus.NOT_PLAYER_LAND, current);
        if (current.ownerId().equals(newOwnerId)) return new TransferResult(TransferStatus.UNCHANGED, current);
        Land existing = landsByOwner.get(newOwnerId);
        if (existing != null && !existing.id().equals(landId)) return new TransferResult(TransferStatus.NEW_OWNER_ALREADY_OWNS_LAND, current);
        storage.updateLandOwner(landId, newOwnerId);
        Land updated = new Land(current.id(), newOwnerId, current.name(), current.createdAt(), current.memberPermissions(), current.protectionSettings());
        landsById.put(landId, updated);
        landsByOwner.remove(current.ownerId());
        landsByOwner.put(newOwnerId, updated);
        Map<UUID, MemberPermissions> members = membersByLand.get(landId);
        if (members != null && members.containsKey(newOwnerId)) {
            storage.deleteMember(landId, newOwnerId);
            members.remove(newOwnerId);
        }
        return new TransferResult(TransferStatus.TRANSFERRED, updated);
    }

    public synchronized RenameResult rename(UUID ownerId, String requestedName) throws StorageException {
        Land current = landsByOwner.get(ownerId);
        if (current == null) return new RenameResult(RenameStatus.NO_LAND, null);
        String name = requestedName.strip();
        if (!VALID_NAME.matcher(name).matches()) return new RenameResult(RenameStatus.INVALID_NAME, current);
        Land existing = landsByName.get(normalizedName(name));
        if (existing != null && !existing.id().equals(current.id())) return new RenameResult(RenameStatus.NAME_TAKEN, existing);
        if (current.name().equals(name)) return new RenameResult(RenameStatus.UNCHANGED, current);
        storage.updateLandName(current.id(), name);
        Land renamed = new Land(current.id(), current.ownerId(), name, current.createdAt(), current.memberPermissions(), current.protectionSettings());
        landsById.put(renamed.id(), renamed);
        landsByOwner.put(ownerId, renamed);
        landsByName.remove(normalizedName(current.name()));
        landsByName.put(normalizedName(name), renamed);
        return new RenameResult(RenameStatus.RENAMED, renamed);
    }

    public synchronized int claimCount(UUID landId) {
        return claimCounts.getOrDefault(landId, 0);
    }

    public synchronized int totalClaimCount() {
        return claims.size();
    }

    /**
     * Returns the number of lands currently loaded from storage.
     */
    public synchronized int totalLandCount() {
        return landsById.size();
    }

    /** Returns the number of player-owned lands, excluding server Safe Zones. */
    public synchronized int playerLandCount() {
        return landsById.size() - administrativeLandIds.size();
    }

    /** Returns the number of server-owned Safe Zones. */
    public synchronized int administrativeLandCount() {
        return administrativeLandIds.size();
    }

    /** Returns the chunks protected by normal player lands only. */
    public synchronized int playerClaimCount() {
        return claims.values().stream().filter(landId -> !administrativeLandIds.contains(landId)).mapToInt(ignored -> 1).sum();
    }

    /** Returns the chunks protected by server-owned Safe Zones only. */
    public synchronized int administrativeClaimCount() {
        return claims.values().stream().filter(administrativeLandIds::contains).mapToInt(ignored -> 1).sum();
    }

    /** Returns all active member records, excluding land owners and Safe Zone members. */
    public synchronized int playerMembershipCount() {
        return membersByLand.entrySet().stream()
                .filter(entry -> !administrativeLandIds.contains(entry.getKey()))
                .mapToInt(entry -> entry.getValue().size())
                .sum();
    }

    /**
     * Returns the number of lands where the player is a member, excluding land they own.
     */
    public synchronized int memberLandCount(UUID playerId) {
        return (int) membersByLand.entrySet().stream()
                .filter(entry -> entry.getValue().containsKey(playerId))
                .filter(entry -> !landsById.get(entry.getKey()).ownerId().equals(playerId))
                .count();
    }

    public synchronized List<ClaimedChunk> claims() {
        return claims.entrySet().stream()
                .map(entry -> new ClaimedChunk(entry.getKey(), entry.getValue()))
                .toList();
    }

    public synchronized Optional<Land> land(UUID landId) {
        return Optional.ofNullable(landsById.get(landId));
    }

    public synchronized LandType typeOf(Land land) {
        return administrativeLandIds.contains(land.id()) ? LandType.ADMIN : LandType.PLAYER;
    }

    public synchronized boolean isAdministrative(Land land) {
        return typeOf(land) == LandType.ADMIN;
    }

    public synchronized Optional<Land> administrativeLand() {
        return administrativeLandIds.stream().map(landsById::get).filter(java.util.Objects::nonNull).findFirst();
    }

    /** Adds a trusted player with full access to the server-owned administrative land. */
    public synchronized MemberResult addAdministrativeMember(UUID memberId) throws StorageException {
        Land land = administrativeLand().orElse(null);
        if (land == null) return new MemberResult(MemberStatus.NO_LAND, null, MemberPermissions.NONE);
        Map<UUID, MemberPermissions> members = membersByLand.computeIfAbsent(land.id(), ignored -> new HashMap<>());
        if (members.containsKey(memberId)) return new MemberResult(MemberStatus.ALREADY_MEMBER, land, members.get(memberId));
        LandMember member = new LandMember(land.id(), memberId, land.memberPermissions());
        storage.insertMember(member);
        members.put(memberId, member.permissions());
        return new MemberResult(MemberStatus.ADDED, land, member.permissions());
    }

    /** Removes a trusted player's administrative-land access. */
    public synchronized MemberResult removeAdministrativeMember(UUID memberId) throws StorageException {
        Land land = administrativeLand().orElse(null);
        if (land == null) return new MemberResult(MemberStatus.NO_LAND, null, MemberPermissions.NONE);
        Map<UUID, MemberPermissions> members = membersByLand.getOrDefault(land.id(), Map.of());
        if (!members.containsKey(memberId)) return new MemberResult(MemberStatus.NOT_MEMBER, land, MemberPermissions.NONE);
        storage.deleteMember(land.id(), memberId);
        membersByLand.get(land.id()).remove(memberId);
        return new MemberResult(MemberStatus.REMOVED, land, MemberPermissions.NONE);
    }

    public synchronized List<LandMember> members(UUID landId) {
        return membersByLand.getOrDefault(landId, Map.of()).entrySet().stream()
                .map(entry -> new LandMember(landId, entry.getKey(), entry.getValue()))
                .sorted(java.util.Comparator.comparing(LandMember::playerId))
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
        LandMember member = new LandMember(land.id(), memberId, land.memberPermissions());
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

    /** Changes one individual member right in the server-owned administrative land. */
    public synchronized MemberResult updateAdministrativeMemberPermission(
            UUID memberId,
            MemberPermissions.Permission permission,
            boolean enabled
    ) throws StorageException {
        Land land = administrativeLand().orElse(null);
        if (land == null) return new MemberResult(MemberStatus.NO_LAND, null, MemberPermissions.NONE);
        Map<UUID, MemberPermissions> members = membersByLand.getOrDefault(land.id(), Map.of());
        MemberPermissions current = members.get(memberId);
        if (current == null) return new MemberResult(MemberStatus.NOT_MEMBER, land, MemberPermissions.NONE);
        MemberPermissions updated = current.with(permission, enabled);
        storage.updateMember(new LandMember(land.id(), memberId, updated));
        membersByLand.get(land.id()).put(memberId, updated);
        return new MemberResult(MemberStatus.PERMISSION_UPDATED, land, updated);
    }

    /**
     * Changes the default used for future members of the owner's land. Existing
     * member records are deliberately left untouched because they may have
     * individual rights chosen by the owner.
     */
    public synchronized MemberPermissions updateAllMemberPermissions(UUID ownerId, MemberPermissions.Permission permission, boolean enabled) throws StorageException {
        Land current = landsByOwner.get(ownerId);
        if (current == null) return MemberPermissions.NONE;
        MemberPermissions updated = current.memberPermissions().with(permission, enabled);
        storage.updateLandMemberPermissions(current.id(), updated);
        Land land = new Land(current.id(), current.ownerId(), current.name(), current.createdAt(), updated, current.protectionSettings());
        landsById.put(land.id(), land);
        landsByOwner.put(land.ownerId(), land);
        return updated;
    }

    /** Changes one member-only gameplay setting for the owner's land. */
    public synchronized LandProtectionSettings updateProtectionSetting(
            UUID ownerId,
            LandProtectionSettings.Setting setting,
            boolean enabled
    ) throws StorageException {
        Land current = landsByOwner.get(ownerId);
        if (current == null) return LandProtectionSettings.DEFAULT;
        LandProtectionSettings updated = current.protectionSettings().with(setting, enabled);
        storage.updateLandProtectionSettings(current.id(), updated);
        Land land = new Land(current.id(), current.ownerId(), current.name(), current.createdAt(), current.memberPermissions(), updated);
        landsById.put(land.id(), land);
        landsByOwner.put(land.ownerId(), land);
        return updated;
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

    private String nextAdministrativeName(String requestedName) {
        String base = requestedName == null ? "Safe Zone" : requestedName.strip();
        if (!VALID_NAME.matcher(base).matches()) base = "Safe Zone";
        if (!landsByName.containsKey(normalizedName(base))) return base;
        for (int number = 2; number < 10_000; number++) {
            String candidate = base + " " + number;
            if (!landsByName.containsKey(normalizedName(candidate))) return candidate;
        }
        throw new IllegalStateException("Could not find an available administrative land name");
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

    public record RenameResult(RenameStatus status, Land land) {
    }

    public enum RenameStatus {
        RENAMED,
        UNCHANGED,
        NO_LAND,
        INVALID_NAME,
        NAME_TAKEN
    }

    public record TransferResult(TransferStatus status, Land land) { }

    public enum TransferStatus {
        TRANSFERRED,
        UNCHANGED,
        NOT_PLAYER_LAND,
        NEW_OWNER_ALREADY_OWNS_LAND
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

    public record AdminUnclaimResult(AdminUnclaimStatus status, Land land, int claimCount) {
    }

    public enum AdminUnclaimStatus {
        CLAIM_REMOVED,
        LAND_DELETED,
        UNCLAIMED
    }

    public record AdminClaimResult(AdminClaimStatus status, Land land) {
    }

    public enum AdminClaimStatus {
        CREATED,
        CLAIMED,
        ALREADY_CLAIMED
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
