package io.github.devkruidenier.landward.protection;

import io.github.devkruidenier.landward.model.MemberPermissions;

/** Independent public Safe Zone access and explicitly granted member rights. */
final class SafeZoneAccess {
    private SafeZoneAccess() {}
    static boolean allowed(MemberPermissions.Permission permission, MemberPermissions member,
                           boolean publicBuild, boolean publicContainers, boolean publicEntities) {
        return switch (permission) {
            case BUILD -> member.build() || publicBuild;
            case CONTAINERS -> member.containers() || publicContainers;
            case ENTITIES -> member.entities() || publicEntities;
        };
    }
}
