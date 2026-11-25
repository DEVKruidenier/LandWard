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
    private java.util.function.BiConsumer<UUID,ChunkPosition> personalCleanup=(land,chunk)->{};
    public synchronized void personalCleanup(java.util.function.BiConsumer<UUID,ChunkPosition> cleanup){personalCleanup=cleanup;}
    private final io.github.devkruidenier.landward.blockprotection.ProtectionIndex personalProtections = new io.github.devkruidenier.landward.blockprotection.ProtectionIndex();
    public io.github.devkruidenier.landward.blockprotection.ProtectionIndex personalProtections() { return personalProtections; }

    private static final Pattern VALID_NAME = Pattern.compile("^[\\p{L}\\p{N} _-]{3,24}$");

    private LandStorage storage;
    /** Defaults copied into a land when it is first created. Existing lands keep their own choices. */
    private MemberPermissions playerMemberDefaults;
    private MemberPermissions administrativeMemberDefaults;
    // Readers never acquire the durable mutation monitor: slow SQL cannot stall region event checks.
    private final Map<UUID, Land> landsById = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, io.github.devkruidenier.landward.model.LandFeatures> featuresByLand = new java.util.concurrent.ConcurrentHashMap<>();
    private io.github.devkruidenier.landward.model.EnvironmentSettings playerEnvironmentDefaults = io.github.devkruidenier.landward.model.EnvironmentSettings.defaults(false);
    private io.github.devkruidenier.landward.model.EnvironmentSettings adminEnvironmentDefaults = io.github.devkruidenier.landward.model.EnvironmentSettings.defaults(true);

    public synchronized void environmentDefaults(io.github.devkruidenier.landward.model.EnvironmentSettings player,
                                                 io.github.devkruidenier.landward.model.EnvironmentSettings admin) {
        playerEnvironmentDefaults = player; adminEnvironmentDefaults = admin;
    }
    private final Map<UUID, Land> landsByOwner = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Land> landsByName = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<ChunkPosition, UUID> claims = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Integer> claimCounts = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, MemberPermissions>> membersByLand = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<UUID> administrativeLandIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

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
        io.github.devkruidenier.landward.blockprotection.ProtectionBackup.validate(snapshot);
        personalProtections.clear();
        snapshot.placements().forEach(personalProtections::put);
        snapshot.protections().forEach(personalProtections::put);
        landsById.clear();
        featuresByLand.clear();
        for (var features : snapshot.features()) featuresByLand.put(features.landId(), features);
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
            membersByLand.computeIfAbsent(member.landId(), ignored -> new java.util.concurrent.ConcurrentHashMap<>())
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
        var features = new io.github.devkruidenier.landward.model.LandFeatures(land.id(), playerEnvironmentDefaults, List.of());
        storage.insertLandWithFeatures(land, new ClaimedChunk(initialPosition, land.id()), features, false);
        landsById.put(land.id(), land);
        landsByOwner.put(ownerId, land);
        landsByName.put(normalizedName(name), land);
        claims.put(initialPosition, land.id());
        claimCounts.put(land.id(), 1);
        featuresByLand.put(land.id(), features);
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
            var features = new io.github.devkruidenier.landward.model.LandFeatures(administrativeLand.id(), adminEnvironmentDefaults, List.of());
            storage.insertLandWithFeatures(administrativeLand, new ClaimedChunk(position, administrativeLand.id()), features, true);
            landsById.put(administrativeLand.id(), administrativeLand);
            landsByName.put(normalizedName(administrativeLand.name()), administrativeLand);
            administrativeLandIds.add(administrativeLand.id());
            featuresByLand.put(administrativeLand.id(), features);
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
        forgetPersonalChunk(land.id(), position);
        forgetChunkWarps(land.id(), position);
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
        personalCleanup.accept(land.id(),null);
        personalProtections.prune(p -> !p.landId().equals(land.id()), p -> !p.landId().equals(land.id()));
        featuresByLand.remove(land.id());
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
        forgetPersonalChunk(landId, position);
        forgetChunkWarps(landId, position);
        claims.remove(position);
        claimCounts.put(landId, count - 1);
        return new AdminUnclaimResult(AdminUnclaimStatus.CLAIM_REMOVED, land, count - 1);
    }

    public Optional<Land> landAt(ChunkPosition position) {
        return Optional.ofNullable(claims.get(position)).map(landsById::get);
    }

    public io.github.devkruidenier.landward.model.LandFeatures features(UUID landId) {
        if (!landsById.containsKey(landId)) throw new IllegalArgumentException("Unknown land");
        return featuresByLand.getOrDefault(landId, new io.github.devkruidenier.landward.model.LandFeatures(landId,
                io.github.devkruidenier.landward.model.EnvironmentSettings.legacy(), List.of()));
    }

    public synchronized void environment(UUID landId, io.github.devkruidenier.landward.model.EnvironmentSettings settings) throws StorageException {
        var current = features(landId);
        saveFeatures(new io.github.devkruidenier.landward.model.LandFeatures(landId, settings, current.warps()));
    }

    /** Read/modify/persist under the same lock: different regions cannot lose each other's toggles. */
    public synchronized void toggleEnvironment(UUID landId, UUID actor, boolean admin,
            io.github.devkruidenier.landward.model.EnvironmentSettings.Rule rule) throws StorageException {
        Land land = landsById.get(landId);
        if (land == null || !admin && !land.ownerId().equals(actor)) return;
        var settings = features(landId).environment();
        environment(landId, settings.with(rule, !settings.allows(rule)));
    }

    public synchronized void addWarp(UUID owner, UUID landId, io.github.devkruidenier.landward.model.LandWarp warp, int limit) throws StorageException {
        var land = landsById.get(landId);
        if (land == null || isAdministrative(land) || !land.ownerId().equals(owner)
                || !landId.equals(claims.get(warp.chunk()))) throw new IllegalArgumentException("Warp destination is outside owned land");
        var current = features(landId);
        if (limit >= 0 && current.warps().size() >= limit) throw new IllegalArgumentException("Warp limit reached");
        var warps = new java.util.ArrayList<>(current.warps()); warps.add(warp);
        saveFeatures(new io.github.devkruidenier.landward.model.LandFeatures(landId, current.environment(), warps));
    }

    public synchronized void renameWarp(UUID owner, UUID landId, UUID warpId, String name) throws StorageException {
        var land = landsById.get(landId);
        if (land == null || !land.ownerId().equals(owner) || isAdministrative(land)) throw new IllegalArgumentException("Not the owner");
        var current = features(landId);
        if (current.warps().stream().noneMatch(warp -> warp.id().equals(warpId))) throw new IllegalArgumentException("Warp removed");
        saveFeatures(new io.github.devkruidenier.landward.model.LandFeatures(landId, current.environment(),
                current.warps().stream().map(warp -> warp.id().equals(warpId) ? warp.renamed(name) : warp).toList()));
    }

    public synchronized void deleteWarp(UUID owner, UUID landId, UUID warpId) throws StorageException {
        var land = landsById.get(landId);
        if (land == null || !land.ownerId().equals(owner) || isAdministrative(land)) throw new IllegalArgumentException("Not the owner");
        var current = features(landId);
        saveFeatures(new io.github.devkruidenier.landward.model.LandFeatures(landId, current.environment(),
                current.warps().stream().filter(warp -> !warp.id().equals(warpId)).toList()));
    }

    public boolean canUseWarps(UUID player, UUID landId) {
        var land = landsById.get(landId);
        return land != null && !isAdministrative(land) && (land.ownerId().equals(player)
                || membersByLand.getOrDefault(landId, Map.of()).containsKey(player));
    }

    private void saveFeatures(io.github.devkruidenier.landward.model.LandFeatures features) throws StorageException {
        storage.updateFeatures(features);
        featuresByLand.put(features.landId(), features);
    }
    private void forgetChunkWarps(UUID landId, ChunkPosition position) {
        var current = featuresByLand.get(landId);
        if (current != null) featuresByLand.put(landId, new io.github.devkruidenier.landward.model.LandFeatures(landId,
                current.environment(), current.warps().stream().filter(warp -> !warp.chunk().equals(position)).toList()));
    }

    public Optional<Land> ownedBy(UUID ownerId) {
        return Optional.ofNullable(landsByOwner.get(ownerId));
    }

    /** Returns all player-owned lands for administrative review; server claims are excluded. */
    public List<Land> playerLands() {
        return landsById.values().stream()
                .filter(land -> !administrativeLandIds.contains(land.id()))
                .sorted(java.util.Comparator.comparing(Land::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Returns every player land where this UUID is a trusted member, never server-owned safe zones. */
    public List<Land> memberLands(UUID playerId) {
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
            members.remove(newOwnerId);
        }
        if (!membersByLand.getOrDefault(landId, Map.of()).containsKey(current.ownerId()))
            personalProtections.removeMember(landId, current.ownerId());
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

    public int claimCount(UUID landId) {
        return claimCounts.getOrDefault(landId, 0);
    }

    public int totalClaimCount() {
        return claims.size();
    }

    /**
     * Returns the number of lands currently loaded from storage.
     */
    public int totalLandCount() {
        return landsById.size();
    }

    /** Returns the number of player-owned lands, excluding server Safe Zones. */
    public int playerLandCount() {
        return landsById.size() - administrativeLandIds.size();
    }

    /** Aggregate in-memory counts; warp traversal stops as soon as the highest bucket is reached. */
    public long[] usageCounts() {
        long warps = 0;
        for (var features : featuresByLand.values()) {
            warps += features.warps().size();
            if (warps > 500) break;
        }
        return new long[]{landsById.size() - administrativeLandIds.size(), claims.size(), administrativeLandIds.size(), warps};
    }

    /** Returns the number of server-owned Safe Zones. */
    public int administrativeLandCount() {
        return administrativeLandIds.size();
    }

    /** Returns the chunks protected by normal player lands only. */
    public int playerClaimCount() {
        return claims.values().stream().filter(landId -> !administrativeLandIds.contains(landId)).mapToInt(ignored -> 1).sum();
    }

    /** Returns the chunks protected by server-owned Safe Zones only. */
    public int administrativeClaimCount() {
        return claims.values().stream().filter(administrativeLandIds::contains).mapToInt(ignored -> 1).sum();
    }

    /** Returns all active member records, excluding land owners and Safe Zone members. */
    public int playerMembershipCount() {
        return membersByLand.entrySet().stream()
                .filter(entry -> !administrativeLandIds.contains(entry.getKey()))
                .mapToInt(entry -> entry.getValue().size())
                .sum();
    }

    /**
     * Returns the number of lands where the player is a member, excluding land they own.
     */
    public int memberLandCount(UUID playerId) {
        return (int) membersByLand.entrySet().stream()
                .filter(entry -> entry.getValue().containsKey(playerId))
                .filter(entry -> !landsById.get(entry.getKey()).ownerId().equals(playerId))
                .count();
    }

    public List<ClaimedChunk> claims() {
        return claims.entrySet().stream()
                .map(entry -> new ClaimedChunk(entry.getKey(), entry.getValue()))
                .toList();
    }

    public Optional<Land> land(UUID landId) {
        return Optional.ofNullable(landsById.get(landId));
    }

    public LandType typeOf(Land land) {
        return administrativeLandIds.contains(land.id()) ? LandType.ADMIN : LandType.PLAYER;
    }

    public boolean isAdministrative(Land land) {
        return typeOf(land) == LandType.ADMIN;
    }

    public Optional<Land> administrativeLand() {
        return administrativeLandIds.stream().map(landsById::get).filter(java.util.Objects::nonNull).findFirst();
    }

    /** Adds a trusted player with full access to the server-owned administrative land. */
    public synchronized MemberResult addAdministrativeMember(UUID memberId) throws StorageException {
        Land land = administrativeLand().orElse(null);
        if (land == null) return new MemberResult(MemberStatus.NO_LAND, null, MemberPermissions.NONE);
        Map<UUID, MemberPermissions> members = membersByLand.computeIfAbsent(land.id(), ignored -> new java.util.concurrent.ConcurrentHashMap<>());
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
        personalProtections.removeMember(land.id(), memberId);
        membersByLand.get(land.id()).remove(memberId);
        return new MemberResult(MemberStatus.REMOVED, land, MemberPermissions.NONE);
    }

    public List<LandMember> members(UUID landId) {
        return membersByLand.getOrDefault(landId, Map.of()).entrySet().stream()
                .map(entry -> new LandMember(landId, entry.getKey(), entry.getValue()))
                .sorted(java.util.Comparator.comparing(LandMember::playerId))
                .toList();
    }

    public MemberPermissions permissions(UUID playerId, UUID landId) {
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
        Map<UUID, MemberPermissions> members = membersByLand.computeIfAbsent(land.id(), ignored -> new java.util.concurrent.ConcurrentHashMap<>());
        if (members.containsKey(memberId)) {
            return new MemberResult(MemberStatus.ALREADY_MEMBER, land, members.get(memberId));
        }
        LandMember member = new LandMember(land.id(), memberId, land.memberPermissions());
        storage.insertMember(member);
        members.put(memberId, member.permissions());
        return new MemberResult(MemberStatus.ADDED, land, member.permissions());
    }

    /** GUI removals remain bound to the land originally selected, including after an ownership change. */
    public synchronized MemberResult removeMember(UUID ownerId, UUID selectedLand, UUID memberId) throws StorageException {
        var land=landsById.get(selectedLand);
        if(land==null || !land.ownerId().equals(ownerId))return new MemberResult(MemberStatus.NO_LAND,land,MemberPermissions.NONE);
        return removeMember(ownerId,memberId);
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
        personalProtections.removeMember(land.id(), memberId);
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

    public boolean personalMember(UUID landId, UUID player) {
        Land land=landsById.get(landId);
        return land!=null && !isAdministrative(land) && (land.ownerId().equals(player) || membersByLand.getOrDefault(landId,Map.of()).containsKey(player));
    }
    public boolean managePersonal(io.github.devkruidenier.landward.blockprotection.ProtectionActor actor,
            io.github.devkruidenier.landward.blockprotection.BlockProtection p) {
        Land land=landsById.get(p.landId());
        return land!=null && !isAdministrative(land) && (actor.adminManage()
                || personalMember(p.landId(),actor.id()) && (p.owner().equals(actor.id()) && actor.own()
                || land.ownerId().equals(actor.id()) && actor.landManage()));
    }
    public boolean personalAccess(UUID player, io.github.devkruidenier.landward.blockprotection.BlockProtection p, boolean ownerBypass, boolean adminBypass) {
        if(adminBypass)return true;
        var land=landsById.get(p.landId());
        return personalMember(p.landId(),player) && (p.owner().equals(player) || p.shared().contains(player)
                || ownerBypass && land!=null && land.ownerId().equals(player));
    }
    public synchronized void recordPlacement(io.github.devkruidenier.landward.blockprotection.BlockPlacement p) throws StorageException {
        if(!personalMember(p.landId(),p.owner()) || !p.landId().equals(claims.get(p.position().chunk())))return;
        storage.writeBlockProtections(List.of(),List.of(),List.of(p),List.of());
        personalProtections.forget(p.position()); personalProtections.put(p);
    }
    public synchronized void forgetPersonal(java.util.List<io.github.devkruidenier.landward.blockprotection.BlockKey> positions) throws StorageException {
        storage.writeBlockProtections(List.of(),List.of(),List.of(),positions);
        positions.forEach(personalProtections::forget);
    }
    public synchronized io.github.devkruidenier.landward.blockprotection.BlockProtection protect(
            io.github.devkruidenier.landward.blockprotection.ProtectionActor actor, UUID landId, String material,
            java.util.List<io.github.devkruidenier.landward.blockprotection.BlockKey> blocks,
            java.util.Map<io.github.devkruidenier.landward.blockprotection.BlockKey,UUID> generations, int maximum) throws StorageException {
        var land=landsById.get(landId);
        if(!actor.create() || !personalMember(landId,actor.id()) || blocks.isEmpty() || blocks.size()>2)throw new IllegalArgumentException("denied");
        for(var b:blocks) {
            if(!landId.equals(claims.get(b.chunk())) || personalProtections.at(b)!=null)throw new IllegalArgumentException("changed");
            var placement=personalProtections.placement(b);
            if(placement==null) { if(!land.ownerId().equals(actor.id()) || generations.containsKey(b))throw new IllegalArgumentException("unknown-owner"); }
            else if(!placement.owner().equals(actor.id()) || !placement.material().equals(material)
                    || !placement.generation().equals(generations.get(b)))throw new IllegalArgumentException("unknown-owner");
        }
        long count=personalProtections.all().stream().filter(p->p.owner().equals(actor.id())).count();
        if(maximum>=0 && count>=maximum)throw new IllegalArgumentException("limit");
        var p=new io.github.devkruidenier.landward.blockprotection.BlockProtection(UUID.randomUUID(),landId,actor.id(),material,blocks,java.util.Set.of(),System.currentTimeMillis(),0);
        storage.writeBlockProtections(List.of(p),List.of(),List.of(),List.of()); personalProtections.put(p);return p;
    }
    public synchronized io.github.devkruidenier.landward.blockprotection.BlockProtection extendPersonalChest(
            io.github.devkruidenier.landward.blockprotection.ProtectionActor actor, UUID id, long revision,
            io.github.devkruidenier.landward.blockprotection.BlockPlacement placed) throws StorageException {
        var p=personalProtections.get(id);
        if(p==null || p.revision()!=revision || !actor.create() || !actor.own()
                || !p.owner().equals(actor.id()) || !personalMember(p.landId(),actor.id())
                || !p.storage() || p.blocks().size()!=1 || !p.material().endsWith("CHEST")
                || !p.landId().equals(placed.landId()) || !p.owner().equals(placed.owner())
                || !p.material().equals(placed.material()) || personalProtections.at(placed.position())!=null
                || !p.landId().equals(claims.get(placed.position().chunk())))throw new IllegalArgumentException("changed");
        var first=p.blocks().getFirst(); var second=placed.position();
        if(!first.world().equals(second.world()) || first.y()!=second.y()
                || Math.abs(first.x()-second.x())+Math.abs(first.z()-second.z())!=1)
            throw new IllegalArgumentException("changed");
        var expanded=new io.github.devkruidenier.landward.blockprotection.BlockProtection(p.id(),p.landId(),p.owner(),
                p.material(),List.of(first,second),p.shared(),p.created(),p.revision()+1);
        storage.writeBlockProtections(List.of(expanded),List.of(),List.of(placed),List.of());
        personalProtections.put(placed); personalProtections.put(expanded); return expanded;
    }
    public synchronized io.github.devkruidenier.landward.blockprotection.BlockProtection breakPersonalStorage(
            io.github.devkruidenier.landward.blockprotection.ProtectionActor actor, UUID id, long revision,
            io.github.devkruidenier.landward.blockprotection.BlockKey destroyed) throws StorageException {
        var p=personalProtections.get(id);
        if(p==null || p.revision()!=revision || !p.storage() || !actor.own()
                || !p.owner().equals(actor.id()) || !personalMember(p.landId(),actor.id())
                || !p.blocks().contains(destroyed))throw new IllegalArgumentException("changed");
        var remaining=p.blocks().stream().filter(b->!b.equals(destroyed)).toList();
        var saved=remaining.isEmpty()?null:new io.github.devkruidenier.landward.blockprotection.BlockProtection(
                p.id(),p.landId(),p.owner(),p.material(),remaining,p.shared(),p.created(),p.revision()+1);
        storage.writeBlockProtections(saved==null?List.of():List.of(saved),List.of(p.id()),List.of(),List.of(destroyed));
        personalProtections.forget(destroyed);
        if(saved!=null) personalProtections.put(saved);
        return saved;
    }
    public synchronized void sharePersonal(io.github.devkruidenier.landward.blockprotection.ProtectionActor actor, UUID id, long revision, java.util.Set<UUID> shared) throws StorageException {
        var p=personalProtections.get(id);
        // Land-owner administration may remove locks, but never silently rewrite a member's sharing.
        if(p==null || p.revision()!=revision || !(actor.adminManage() || p.owner().equals(actor.id()) && actor.own() && personalMember(p.landId(),actor.id())))throw new IllegalArgumentException("changed");
        if(shared.stream().anyMatch(m->m.equals(p.owner()) || !personalMember(p.landId(),m)))throw new IllegalArgumentException("changed");
        var updated=p.sharing(shared); storage.writeBlockProtections(List.of(updated),List.of(),List.of(),List.of());personalProtections.put(updated);
    }
    public synchronized void removePersonal(io.github.devkruidenier.landward.blockprotection.ProtectionActor actor, UUID id,long revision) throws StorageException {
        var p=personalProtections.get(id);
        if(p==null || p.revision()!=revision || !managePersonal(actor,p))throw new IllegalArgumentException("changed");
        storage.writeBlockProtections(List.of(),List.of(id),List.of(),List.of());personalProtections.remove(id);
    }
    public synchronized MemberResult leavePersonalLand(UUID member, UUID landId) throws StorageException {
        var land=landsById.get(landId);
        if(land==null || land.ownerId().equals(member))return new MemberResult(MemberStatus.NOT_MEMBER,land,MemberPermissions.NONE);
        return removeMember(land.ownerId(),member);
    }
    private void forgetPersonalChunk(UUID land,ChunkPosition chunk) {
        personalCleanup.accept(land,chunk);
        personalProtections.prune(p->!p.landId().equals(land)||p.blocks().stream().noneMatch(b->b.chunk().equals(chunk)),
                p->!p.landId().equals(land)||!p.position().chunk().equals(chunk));
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
