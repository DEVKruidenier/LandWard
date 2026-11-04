package io.github.devkruidenier.landward.model;

import java.time.Instant;
import java.util.UUID;

public record Land(UUID id, UUID ownerId, String name, Instant createdAt) {
}
