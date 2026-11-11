package io.github.devkruidenier.landward.protection;

import io.github.devkruidenier.landward.model.MemberPermissions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SafeZoneAccessTest {
    @Test void visitorsRemainProtectedByDefault() {
        for (var permission : MemberPermissions.Permission.values())
            assertFalse(SafeZoneAccess.allowed(permission, MemberPermissions.NONE, false, false, false));
    }
    @Test void publicBuildDoesNotAlsoOpenContainersOrEntities() {
        assertTrue(SafeZoneAccess.allowed(MemberPermissions.Permission.BUILD, MemberPermissions.NONE, true, false, false));
        assertFalse(SafeZoneAccess.allowed(MemberPermissions.Permission.CONTAINERS, MemberPermissions.NONE, true, false, false));
        assertFalse(SafeZoneAccess.allowed(MemberPermissions.Permission.ENTITIES, MemberPermissions.NONE, true, false, false));
    }
    @Test void publicEntitiesDoNotGrantBuildingOrContainers() {
        assertTrue(SafeZoneAccess.allowed(MemberPermissions.Permission.ENTITIES, MemberPermissions.NONE, false, false, true));
        assertFalse(SafeZoneAccess.allowed(MemberPermissions.Permission.BUILD, MemberPermissions.NONE, false, false, true));
        assertFalse(SafeZoneAccess.allowed(MemberPermissions.Permission.CONTAINERS, MemberPermissions.NONE, false, false, true));
    }
    @Test void memberRightsAreEvaluatedIndividually() {
        for (var granted : MemberPermissions.Permission.values()) {
            var member = MemberPermissions.NONE.with(granted, true);
            for (var checked : MemberPermissions.Permission.values())
                assertEquals(granted == checked, SafeZoneAccess.allowed(checked, member, false, false, false));
        }
    }
}
