package io.github.devkruidenier.landward.storage;

import io.github.devkruidenier.landward.model.ClaimedChunk;
import io.github.devkruidenier.landward.model.Land;

import java.util.List;

public record StorageSnapshot(List<Land> lands, List<ClaimedChunk> claims) {
}
