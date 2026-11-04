package io.github.devkruidenier.landward.storage;

import io.github.devkruidenier.landward.model.ClaimedChunk;
import io.github.devkruidenier.landward.model.Land;
import io.github.devkruidenier.landward.model.LandMember;

import java.util.List;
import java.util.UUID;

public record StorageSnapshot(List<Land> lands, List<ClaimedChunk> claims, List<LandMember> members, List<UUID> adminLandIds) {
}
