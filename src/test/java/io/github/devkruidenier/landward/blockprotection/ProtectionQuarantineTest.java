package io.github.devkruidenier.landward.blockprotection;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProtectionQuarantineTest {
  @TempDir Path directory;

  @Test
  void aRestartDoesNotDiscardUnverifiedOwnershipFailures() throws Exception {
    var file = directory.resolve("quarantine.txt");
    var a = new BlockKey(UUID.randomUUID(), -17, 64, 2);
    var b = new BlockKey(a.world(), -16, 64, 2);
    var first = new ProtectionQuarantine(file);
    first.fail(List.of(a, b));
    var restarted = new ProtectionQuarantine(file);
    assertEquals(Set.of(a, b), restarted.locations());
    restarted.resolve(List.of(a));
    assertEquals(Set.of(b), new ProtectionQuarantine(file).locations());
    restarted.resolve(List.of(b));
    assertTrue(new ProtectionQuarantine(file).locations().isEmpty());
  }

  @Test
  void malformedFailureDataIsNeverSilentlyIgnored() throws Exception {
    var file = directory.resolve("quarantine.txt");
    Files.writeString(file, "broken,data\n");
    assertThrows(java.io.IOException.class, () -> new ProtectionQuarantine(file));
  }
}
