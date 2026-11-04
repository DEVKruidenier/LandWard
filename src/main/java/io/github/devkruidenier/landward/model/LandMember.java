package io.github.devkruidenier.landward.model;

import java.util.UUID;

public record LandMember(UUID landId, UUID playerId, MemberPermissions permissions) {
}
