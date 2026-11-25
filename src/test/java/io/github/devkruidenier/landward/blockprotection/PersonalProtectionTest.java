package io.github.devkruidenier.landward.blockprotection;

import static org.junit.jupiter.api.Assertions.*;

import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.*;
import io.github.devkruidenier.landward.storage.*;
import io.github.devkruidenier.landward.storage.migration.StorageSnapshotBackup;
import io.github.devkruidenier.landward.storage.sqlite.SqliteLandStorage;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class PersonalProtectionTest {
  @TempDir Path directory;
  SqliteLandStorage db;
  LandManager lands;
  Land land;
  final UUID owner = UUID.randomUUID(),
      member = UUID.randomUUID(),
      shared = UUID.randomUUID(),
      world = UUID.randomUUID();

  @BeforeEach
  void setup() throws Exception {
    db = new SqliteLandStorage(directory.resolve("source.db"));
    db.initialize();
    lands = new LandManager(db);
    lands.load();
    land = lands.create(owner, "Amsterdam", new ChunkPosition(world, 0, 0)).land();
    lands.addMember(owner, member);
    lands.addMember(owner, shared);
  }

  ProtectionActor actor(UUID id) {
    return new ProtectionActor(id, true, true, true, false);
  }

  BlockKey key(int x) {
    return new BlockKey(world, x, 64, 2);
  }

  BlockPlacement placement(UUID player, int x) throws Exception {
    var p = new BlockPlacement(key(x), land.id(), player, "CHEST", UUID.randomUUID());
    lands.recordPlacement(p);
    return p;
  }

  BlockProtection lock(UUID player, int x) throws Exception {
    var p = placement(player, x);
    return lands.protect(
        actor(player), land.id(), "CHEST", List.of(key(x)), Map.of(key(x), p.generation()), -1);
  }

  @Test
  void landOwnerCanClaimUnknownLegacyBlocksButMembersCannot() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> lands.protect(actor(member), land.id(), "CHEST", List.of(key(1)), Map.of(), -1));
    assertEquals(
        owner,
        lands.protect(actor(owner), land.id(), "CHEST", List.of(key(1)), Map.of(), -1).owner());
  }

  @Test
  void landOwnerCannotSeizeAnotherMembersKnownPlacement() throws Exception {
    var p = placement(member, 1);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            lands.protect(
                actor(owner),
                land.id(),
                "CHEST",
                List.of(key(1)),
                Map.of(key(1), p.generation()),
                -1));
  }

  @Test
  void onlyLockOwnerAndExplicitSharedMembersHavePrivateAccess() throws Exception {
    var p = lock(member, 1);
    assertTrue(lands.personalAccess(member, p, false, false));
    assertFalse(lands.personalAccess(owner, p, false, false));
    assertFalse(lands.personalAccess(shared, p, false, false));
    assertTrue(lands.personalAccess(owner, p, true, false));
    assertTrue(lands.personalAccess(UUID.randomUUID(), p, false, true));
    lands.sharePersonal(actor(member), p.id(), p.revision(), Set.of(shared));
    p = lands.personalProtections().get(p.id());
    assertTrue(lands.personalAccess(shared, p, false, false));
    assertFalse(lands.permissions(shared, land.id()).equals(MemberPermissions.NONE));
    lands.updateMemberPermission(owner, shared, MemberPermissions.Permission.CONTAINERS, false);
    assertFalse(
        lands.permissions(shared, land.id()).containers(),
        "Sharing never changes normal land permissions");
  }

  @Test
  void ownerMayRemoveButCannotSilentlyChangeMembersSharing() throws Exception {
    var p = lock(member, 1);
    assertTrue(lands.managePersonal(actor(owner), p));
    assertThrows(
        IllegalArgumentException.class,
        () -> lands.sharePersonal(actor(owner), p.id(), p.revision(), Set.of(shared)));
    lands.removePersonal(actor(owner), p.id(), p.revision());
    assertNull(lands.personalProtections().at(key(1)));
  }

  @Test
  void unauthorizedMemberCannotRemoveOrEditAnotherLock() throws Exception {
    var p = lock(member, 1);
    assertThrows(
        IllegalArgumentException.class,
        () -> lands.removePersonal(actor(shared), p.id(), p.revision()));
    assertThrows(
        IllegalArgumentException.class,
        () -> lands.sharePersonal(actor(shared), p.id(), p.revision(), Set.of(shared)));
  }

  @Test
  void administrativeRecordManagementDoesNotImplyDirectAccess() throws Exception {
    var p = lock(member, 1);
    var admin = new ProtectionActor(UUID.randomUUID(), false, false, false, true);
    assertTrue(lands.managePersonal(admin, p));
    assertFalse(lands.personalAccess(admin.id(), p, false, false));
    lands.removePersonal(admin, p.id(), p.revision());
    assertTrue(db.load().protections().isEmpty());
  }

  @Test
  void explicitCreationPermissionAndCurrentMembershipAreRequired() throws Exception {
    placement(member, 1);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            lands.protect(
                new ProtectionActor(member, false, true, true, false),
                land.id(),
                "CHEST",
                List.of(key(1)),
                Map.of(),
                -1));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            lands.protect(
                actor(UUID.randomUUID()), land.id(), "CHEST", List.of(key(1)), Map.of(), -1));
  }

  @Test
  void replacementGenerationInvalidatesAnOpenSelection() throws Exception {
    var old = placement(member, 1);
    placement(member, 1);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            lands.protect(
                actor(member),
                land.id(),
                "CHEST",
                List.of(key(1)),
                Map.of(key(1), old.generation()),
                -1));
  }

  @Test
  void staleSharingAndRemovalMenusCannotOverwriteNewerChanges() throws Exception {
    var p = lock(member, 1);
    lands.sharePersonal(actor(member), p.id(), p.revision(), Set.of(shared));
    assertThrows(
        IllegalArgumentException.class,
        () -> lands.sharePersonal(actor(member), p.id(), p.revision(), Set.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> lands.removePersonal(actor(member), p.id(), p.revision()));
    assertEquals(Set.of(shared), db.load().protections().getFirst().shared());
  }

  @Test
  void sharingWithAVisitorIsRejected() throws Exception {
    var p = lock(member, 1);
    assertThrows(
        IllegalArgumentException.class,
        () -> lands.sharePersonal(actor(member), p.id(), p.revision(), Set.of(UUID.randomUUID())));
  }

  @Test
  void protectionLimitCountsLogicalUnitsAcrossAllPlayerLands() throws Exception {
    lock(member, 1);
    var p = placement(member, 2);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            lands.protect(
                actor(member),
                land.id(),
                "CHEST",
                List.of(key(2)),
                Map.of(key(2), p.generation()),
                1));
    var second = lands.create(shared, "Rotterdam", new ChunkPosition(world, 1, 0)).land();
    lands.addMember(shared, member);
    var other = new BlockPlacement(key(17), second.id(), member, "CHEST", UUID.randomUUID());
    lands.recordPlacement(other);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            lands.protect(
                actor(member),
                second.id(),
                "CHEST",
                List.of(key(17)),
                Map.of(key(17), other.generation()),
                1));
    assertNotNull(
        lands.protect(
            actor(member),
            second.id(),
            "CHEST",
            List.of(key(17)),
            Map.of(key(17), other.generation()),
            -1));
  }

  @Test
  void doubleChestUsesOneRecordAndBothLocations() throws Exception {
    var a = placement(member, 1);
    var b = placement(member, 2);
    var p =
        lands.protect(
            actor(member),
            land.id(),
            "CHEST",
            List.of(key(2), key(1)),
            Map.of(key(1), a.generation(), key(2), b.generation()),
            1);
    assertEquals(p, lands.personalProtections().at(key(1)));
    assertEquals(p, lands.personalProtections().at(key(2)));
    assertEquals(1, db.load().protections().size());
    assertThrows(
        IllegalArgumentException.class,
        () -> lands.protect(actor(owner), land.id(), "CHEST", List.of(key(1)), Map.of(), -1));
  }

  @Test
  void removingMemberCleansOwnedAndSharedLocksWithoutTouchingOthers() throws Exception {
    var mine = lock(member, 1);
    var other = lock(shared, 2);
    lands.sharePersonal(actor(shared), other.id(), 0, Set.of(member));
    lands.removeMember(owner, member);
    assertNull(lands.personalProtections().get(mine.id()));
    assertEquals(Set.of(), lands.personalProtections().get(other.id()).shared());
    assertEquals(1, db.load().protections().size());
    assertEquals(Set.of(), db.load().protections().getFirst().shared());
    assertTrue(db.load().placements().stream().noneMatch(p -> p.owner().equals(member)));
  }

  @Test
  void voluntarilyLeavingUsesTheSameAtomicCleanup() throws Exception {
    lock(member, 1);
    lands.leavePersonalLand(member, land.id());
    assertTrue(db.load().protections().isEmpty());
    assertFalse(lands.personalMember(land.id(), member));
  }

  @Test
  void ownerTransferPreservesNewOwnerLocksAndRemovesDepartedOwnerLocks() throws Exception {
    var old = lock(owner, 1);
    var next = lock(member, 2);
    var other = lock(shared, 3);
    lands.sharePersonal(actor(shared), other.id(), 0, Set.of(owner, member));
    lands.transferOwnership(land.id(), member);
    assertNull(lands.personalProtections().get(old.id()));
    assertEquals(member, lands.personalProtections().get(next.id()).owner());
    assertEquals(Set.of(member), lands.personalProtections().get(other.id()).shared());
    assertTrue(lands.managePersonal(actor(member), lands.personalProtections().get(other.id())));
    assertFalse(lands.managePersonal(actor(owner), lands.personalProtections().get(other.id())));
    lands.load();
    assertEquals(2, lands.personalProtections().all().size());
  }

  @Test
  void formerOwnerWhoRemainsMemberKeepsPersonalLocks() throws Exception {
    var p = lock(owner, 1);
    db.insertMember(new LandMember(land.id(), owner, MemberPermissions.FULL));
    lands.load();
    lands.transferOwnership(land.id(), member);
    assertEquals(owner, lands.personalProtections().get(p.id()).owner());
    lands.load();
    assertNotNull(lands.personalProtections().get(p.id()));
  }

  @Test
  void unclaimingEitherPartOfCrossChunkUnitRemovesTheWholeLock() throws Exception {
    lands.claim(owner, new ChunkPosition(world, 1, 0), -1, false);
    var a = placement(member, 15);
    var b = placement(member, 16);
    var p =
        lands.protect(
            actor(member),
            land.id(),
            "CHEST",
            List.of(key(15), key(16)),
            Map.of(key(15), a.generation(), key(16), b.generation()),
            -1);
    lock(shared, 2);
    lands.unclaim(owner, new ChunkPosition(world, 1, 0));
    assertNull(lands.personalProtections().get(p.id()));
    assertEquals(1, db.load().protections().size());
    lands.claim(owner, new ChunkPosition(world, 1, 0), -1, false);
    assertNull(lands.personalProtections().at(key(16)));
  }

  @Test
  void landDeletionRemovesLocksSharesAndPlacementRecords() throws Exception {
    lock(member, 1);
    lands.deleteOwnedLand(owner);
    assertTrue(db.load().protections().isEmpty());
    assertTrue(db.load().placements().isEmpty());
  }

  @Test
  void placementOutsideSelectedLandCannotBeClaimed() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> lands.protect(actor(owner), land.id(), "CHEST", List.of(key(17)), Map.of(), -1));
  }

  @Test
  void safeZonesNeverSupportPersonalLocks() throws Exception {
    var safe = lands.claimAdministration(new ChunkPosition(world, 1, 0)).land();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            lands.protect(
                new ProtectionActor(owner, true, true, true, true),
                safe.id(),
                "CHEST",
                List.of(key(17)),
                Map.of(),
                -1));
  }

  @Test
  void locksAndPlacementGenerationsSurviveRestartBackupAndMigration() throws Exception {
    var p = lock(member, 1);
    lands.sharePersonal(actor(member), p.id(), 0, Set.of(shared));
    var before = db.load();
    lands.load();
    assertEquals(before.protections(), lands.personalProtections().all());
    var file = directory.resolve("personal.lwbak");
    var backup = new StorageSnapshotBackup();
    backup.write(file, before);
    assertEquals(before, backup.read(file));
    var target = new SqliteLandStorage(directory.resolve("target.db"));
    target.initialize();
    target.importSnapshotAtomically(before);
    assertEquals(before, target.load());
    target.clearSnapshotAtomically();
    assertTrue(target.load().protections().isEmpty());
    assertTrue(target.load().placements().isEmpty());
  }

  @Test
  void duplicateLocationFailureRollsBackEveryLockRow() throws Exception {
    var p = lock(member, 1);
    var collision =
        new BlockProtection(
            UUID.randomUUID(), land.id(), shared, "CHEST", List.of(key(1)), Set.of(), 1, 0);
    assertThrows(
        StorageException.class,
        () -> db.writeBlockProtections(List.of(collision), List.of(), List.of(), List.of()));
    assertEquals(List.of(p), db.load().protections());
  }

  @Test
  void malformedSnapshotFailsValidationRatherThanGrantingAccess() throws Exception {
    var p =
        new BlockProtection(
            UUID.randomUUID(),
            land.id(),
            UUID.randomUUID(),
            "CHEST",
            List.of(key(1)),
            Set.of(),
            1,
            0);
    var s = db.load();
    assertThrows(
        StorageException.class,
        () ->
            ProtectionBackup.validate(
                new StorageSnapshot(
                    s.lands(),
                    s.claims(),
                    s.members(),
                    s.adminLandIds(),
                    s.features(),
                    List.of(p),
                    List.of())));
  }

  @Test
  void slowProtectionPersistenceNeverBlocksRegionAccessLookups() throws Exception {
    var entered = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    LandStorage delayed =
        (LandStorage)
            java.lang.reflect.Proxy.newProxyInstance(
                LandStorage.class.getClassLoader(),
                new Class<?>[] {LandStorage.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("writeBlockProtections")) {
                    entered.countDown();
                    assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS));
                  }
                  try {
                    return method.invoke(db, args);
                  } catch (java.lang.reflect.InvocationTargetException e) {
                    throw e.getCause();
                  }
                });
    lands.replaceStorage(delayed);
    var error = new java.util.concurrent.atomic.AtomicReference<Throwable>();
    var worker =
        Thread.startVirtualThread(() -> {
          try {
            lands.protect(actor(owner), land.id(), "CHEST", List.of(key(1)), Map.of(), -1);
          } catch (Throwable e) {
            error.set(e);
          }
        });
    try {
      assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
      assertTimeoutPreemptively(
          java.time.Duration.ofMillis(250),
          () -> {
            assertEquals(land.id(), lands.landAt(key(1).chunk()).orElseThrow().id());
            assertTrue(lands.permissions(owner, land.id()).containers());
            assertTrue(lands.personalMember(land.id(), member));
            assertEquals(1, lands.claimCount(land.id()));
          });
    } finally {
      release.countDown();
      worker.join();
    }
    assertNull(error.get());
  }
}
