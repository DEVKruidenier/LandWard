package io.github.devkruidenier.landward.storage.mysql;

import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.model.LandType;
import io.github.devkruidenier.landward.model.MemberPermissions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in contract test for a real MySQL-compatible server. It intentionally
 * remains skipped for ordinary contributor builds; set LANDWARD_MYSQL_INTEGRATION=true
 * plus the connection variables to exercise MariaDB/MySQL before a release.
 */
final class MySqlLandStorageIntegrationTest {

    @Test
    void persistsPlayerAndAdministrativeClaimsOnRealMySql() throws Exception {
        assumeTrue(Boolean.parseBoolean(System.getenv("LANDWARD_MYSQL_INTEGRATION")),
                "Set LANDWARD_MYSQL_INTEGRATION=true to run against a real MySQL-compatible server.");

        try (MySqlLandStorage storage = new MySqlLandStorage(
                setting("LANDWARD_MYSQL_HOST", "127.0.0.1"),
                Integer.parseInt(setting("LANDWARD_MYSQL_PORT", "3306")),
                required("LANDWARD_MYSQL_DATABASE"),
                required("LANDWARD_MYSQL_USER"),
                required("LANDWARD_MYSQL_PASSWORD"),
                false,
                2
        )) {
            storage.initialize();
            LandManager manager = new LandManager(storage);
            manager.load();
            int claimCountBefore = manager.totalClaimCount();

            UUID owner = UUID.randomUUID();
            UUID member = UUID.randomUUID();
            UUID world = UUID.randomUUID();
            ChunkPosition home = new ChunkPosition(world, 2, 3);
            ChunkPosition extension = new ChunkPosition(world, 3, 3);
            var created = manager.create(owner, "MySQL " + UUID.randomUUID().toString().substring(0, 8), home);
            assertEquals(LandManager.CreateStatus.CREATED, created.status());
            var playerLand = created.land();
            assertEquals(LandManager.ClaimStatus.CLAIMED, manager.claim(owner, extension, -1, true).status());
            assertEquals(LandManager.MemberStatus.ADDED, manager.addMember(owner, member).status());
            assertEquals(LandManager.MemberStatus.PERMISSION_UPDATED,
                    manager.updateMemberPermission(owner, member, MemberPermissions.Permission.CONTAINERS, false).status());

            ChunkPosition adminChunk = new ChunkPosition(world, 12, 3);
            var adminLand = manager.claimAdministration(adminChunk).land();
            UUID administratorMember = UUID.randomUUID();
            assertEquals(LandManager.MemberStatus.ADDED, manager.addAdministrativeMember(administratorMember).status());

            LandManager reloaded = new LandManager(storage);
            reloaded.load();
            assertEquals(claimCountBefore + 3, reloaded.totalClaimCount());
            assertEquals(playerLand.id(), reloaded.landAt(home).orElseThrow().id());
            assertTrue(!reloaded.permissions(member, playerLand.id()).containers());
            assertEquals(LandType.ADMIN, reloaded.typeOf(reloaded.landAt(adminChunk).orElseThrow()));
            assertEquals(MemberPermissions.FULL, reloaded.permissions(administratorMember, adminLand.id()));
        }
    }

    private static String setting(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        assumeTrue(value != null && !value.isBlank(), "Missing required integration setting: " + name);
        return value;
    }
}
