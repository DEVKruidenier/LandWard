package io.github.devkruidenier.landward.protection;

import io.github.devkruidenier.landward.model.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.devkruidenier.landward.protection.PlayerClaimInteractionAccess.Target.*;

class PlayerClaimInteractionAccessTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final Land denied = new Land(UUID.randomUUID(), owner, "Rotterdam", Instant.EPOCH,
            MemberPermissions.NONE, new LandProtectionSettings(false, false, false, false, false, false));

    @Test void ownerCanOpenDoorAndUseControlsWithAllMemberSettingsDenied() {
        assertTrue(PlayerClaimInteractionAccess.allowed(owner, denied, MemberPermissions.FULL, DOOR));
        assertTrue(PlayerClaimInteractionAccess.allowed(owner, denied, MemberPermissions.FULL, CONTROL));
        assertFalse(denied.protectionSettings().doors());
        assertFalse(denied.protectionSettings().interactions());
    }

    @Test void ownershipIsIndependentOfDefaultOrCachedMemberRights() {
        for (var target : PlayerClaimInteractionAccess.Target.values()) {
            assertTrue(PlayerClaimInteractionAccess.allowed(owner, denied, MemberPermissions.NONE, target));
        }
    }

    @Test void membersStillRespectDisabledDoorAndControlSettings() {
        assertFalse(PlayerClaimInteractionAccess.allowed(other, denied, MemberPermissions.FULL, DOOR));
        assertFalse(PlayerClaimInteractionAccess.allowed(other, denied, MemberPermissions.FULL, CONTROL));
        assertTrue(PlayerClaimInteractionAccess.allowed(other, denied, MemberPermissions.FULL, BUILD));
        assertTrue(PlayerClaimInteractionAccess.allowed(other, denied, MemberPermissions.FULL, CONTAINER));
    }

    @Test void enabledMemberSettingsDoNotGrantVisitorsAccess() {
        var enabled = new Land(denied.id(), owner, denied.name(), Instant.EPOCH,
                MemberPermissions.FULL, LandProtectionSettings.DEFAULT);
        for (var target : PlayerClaimInteractionAccess.Target.values()) {
            assertFalse(PlayerClaimInteractionAccess.allowed(other, enabled, MemberPermissions.NONE, target));
        }
    }

    @Test void allowedDoorsDoNotGrantMemberBuildOrContainerRights() {
        var enabled = new Land(denied.id(), owner, denied.name(), Instant.EPOCH,
                MemberPermissions.FULL, LandProtectionSettings.DEFAULT);
        var member = new MemberPermissions(false, false, true);
        assertTrue(PlayerClaimInteractionAccess.allowed(other, enabled, member, DOOR));
        assertTrue(PlayerClaimInteractionAccess.allowed(other, enabled, member, CONTROL));
        assertFalse(PlayerClaimInteractionAccess.allowed(other, enabled, member, BUILD));
        assertFalse(PlayerClaimInteractionAccess.allowed(other, enabled, member, CONTAINER));
    }

    @Test void transferringOwnershipTransfersOwnerAccess() {
        var transferred = new Land(denied.id(), other, denied.name(), Instant.EPOCH,
                denied.memberPermissions(), denied.protectionSettings());
        assertTrue(PlayerClaimInteractionAccess.allowed(other, transferred, MemberPermissions.NONE, DOOR));
        assertFalse(PlayerClaimInteractionAccess.allowed(owner, transferred, MemberPermissions.NONE, DOOR));
    }
}
