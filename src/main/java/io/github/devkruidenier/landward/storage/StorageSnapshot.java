package io.github.devkruidenier.landward.storage;

import io.github.devkruidenier.landward.model.ClaimedChunk;
import io.github.devkruidenier.landward.model.Land;
import io.github.devkruidenier.landward.model.LandMember;

import java.util.List;
import java.util.UUID;

public record StorageSnapshot(List<Land> lands, List<ClaimedChunk> claims, List<LandMember> members, List<UUID> adminLandIds,
                              List<io.github.devkruidenier.landward.model.LandFeatures> features,
                              List<io.github.devkruidenier.landward.blockprotection.BlockProtection> protections,
                              List<io.github.devkruidenier.landward.blockprotection.BlockPlacement> placements) {
    public StorageSnapshot(List<Land> lands, List<ClaimedChunk> claims, List<LandMember> members, List<UUID> adminLandIds,
            List<io.github.devkruidenier.landward.model.LandFeatures> features) {
        this(lands, claims, members, adminLandIds, features, List.of(), List.of());
    }
    public StorageSnapshot(List<Land> lands, List<ClaimedChunk> claims, List<LandMember> members, List<UUID> adminLandIds) {
        this(lands, claims, members, adminLandIds, List.of());
    }
}
