package io.github.devkruidenier.landward.protection;

import io.github.devkruidenier.landward.model.Land;
import io.github.devkruidenier.landward.model.MemberPermissions;
import java.util.UUID;

/** Member interaction switches never revoke the player-land owner's access. */
final class PlayerClaimInteractionAccess {
    enum Target { BUILD, CONTAINER, DOOR, CONTROL }

    private PlayerClaimInteractionAccess() {}

    static boolean allowed(UUID player, Land land, MemberPermissions permissions, Target target) {
        if (land.ownerId().equals(player)) return true;
        if (permissions.equals(MemberPermissions.NONE)) return false;
        return switch (target) {
            case BUILD -> permissions.build();
            case CONTAINER -> permissions.containers();
            case DOOR -> land.protectionSettings().doors();
            case CONTROL -> land.protectionSettings().interactions();
        };
    }
}
