package io.github.devkruidenier.landward.blockprotection;

import java.util.UUID;

/** Permission snapshot captured on the player-owning thread immediately before a mutation. */
public record ProtectionActor(
    UUID id, boolean create, boolean own, boolean landManage, boolean adminManage) {}
