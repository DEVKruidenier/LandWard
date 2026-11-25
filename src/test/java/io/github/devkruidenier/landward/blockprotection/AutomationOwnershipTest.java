package io.github.devkruidenier.landward.blockprotection;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class AutomationOwnershipTest {
  final UUID world = UUID.randomUUID(),
      land = UUID.randomUUID(),
      owner = UUID.randomUUID(),
      other = UUID.randomUUID();

  BlockKey key(int x, int y) {
    return new BlockKey(world, x, y, 0);
  }

  BlockPlacement hopper(int x, int y, UUID who) {
    return new BlockPlacement(key(x, y), land, who, "HOPPER", UUID.randomUUID());
  }

  BlockProtection chest() {
    return new BlockProtection(
        UUID.randomUUID(), land, owner, "CHEST", List.of(key(0, 65)), Set.of(other), 1, 0);
  }

  @Test
  void copperChestVariantsProtectTheirSameOwnerHopperNetwork() {
    for (var material :
        List.of(
            "COPPER_CHEST",
            "EXPOSED_COPPER_CHEST",
            "WEATHERED_COPPER_CHEST",
            "OXIDIZED_COPPER_CHEST",
            "WAXED_COPPER_CHEST",
            "WAXED_EXPOSED_COPPER_CHEST",
            "WAXED_WEATHERED_COPPER_CHEST",
            "WAXED_OXIDIZED_COPPER_CHEST")) {
      var lock =
          new BlockProtection(
              UUID.randomUUID(), land, owner, material, List.of(key(0, 65)), Set.of(), 1, 0);
      var map =
          AutomationOwnership.build(
              1, List.of(hopper(0, 64, owner), hopper(1, 64, other)), List.of(lock));
      assertEquals(owner, map.privateHoppers().get(key(0, 64)), material);
      assertFalse(map.privateHoppers().containsKey(key(1, 64)), material);
    }
  }

  @Test
  void onlyKnownSameOwnerHoppersJoinThePrivateAutomationNetwork() {
    var map =
        AutomationOwnership.build(
            7,
            List.of(
                hopper(0, 64, owner),
                hopper(1, 64, owner),
                hopper(2, 64, other),
                hopper(3, 64, owner)),
            List.of(chest()));
    assertEquals(owner, map.privateHoppers().get(key(0, 64)));
    assertEquals(owner, map.privateHoppers().get(key(1, 64)));
    assertFalse(map.privateHoppers().containsKey(key(2, 64)));
    assertFalse(map.privateHoppers().containsKey(key(3, 64)));
    assertEquals(7, map.revision());
  }

  @Test
  void everyPotentialSourceLockIsRetainedForSharedAccessIntersection() {
    var first = chest();
    var second =
        new BlockProtection(
            UUID.randomUUID(), land, owner, "BARREL", List.of(key(1, 65)), Set.of(), 1, 0);
    var graph =
        AutomationOwnership.build(
            1, List.of(hopper(0, 64, owner), hopper(1, 64, owner)), List.of(first, second));
    assertEquals(Set.of(first.id(), second.id()), graph.origins().get(key(0, 64)));
    assertEquals(graph.origins().get(key(0, 64)), graph.origins().get(key(1, 64)));
  }

  @Test
  void unknownLegacyHoppersAreNotAssignedToTheChestOwner() {
    assertTrue(
        AutomationOwnership.build(1, List.of(), List.of(chest())).privateHoppers().isEmpty());
  }

  @Test
  void unrelatedHoppersAndDoorsDoNotBecomePrivateAutomation() {
    var door =
        new BlockProtection(
            UUID.randomUUID(), land, owner, "IRON_DOOR", List.of(key(0, 65)), Set.of(), 1, 0);
    assertTrue(
        AutomationOwnership.build(1, List.of(hopper(0, 64, owner)), List.of(door))
            .privateHoppers()
            .isEmpty());
    assertTrue(
        AutomationOwnership.build(1, List.of(hopper(8, 64, owner)), List.of(chest()))
            .privateHoppers()
            .isEmpty());
  }

  @Test
  void removingTheOriginatingLockRestoresOrdinaryHopperPermissions() {
    var placements = List.of(hopper(0, 64, owner), hopper(1, 64, owner));
    assertEquals(
        2, AutomationOwnership.build(1, placements, List.of(chest())).privateHoppers().size());
    assertTrue(AutomationOwnership.build(2, placements, List.of()).privateHoppers().isEmpty());
  }

  @Test
  void largeNetworksBuildWithoutWorldScansOrPerInteractionTraversal() {
    var placements = new ArrayList<BlockPlacement>();
    for (int x = 0; x < 10_000; x++) placements.add(hopper(x, 64, owner));
    var graph =
        assertTimeoutPreemptively(
            java.time.Duration.ofSeconds(5),
            () -> AutomationOwnership.build(1, placements, List.of(chest())));
    assertEquals(10_000, graph.privateHoppers().size());
    assertEquals(owner, graph.privateHoppers().get(key(9999, 64)));
  }
}
