package io.github.devkruidenier.landward.blockprotection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Durable failure locations, independent of a failing SQL backend. Normal writes do no file I/O.
 */
public final class ProtectionQuarantine {
  private final Path file;
  private final Set<BlockKey> failed = new HashSet<>();

  public ProtectionQuarantine(Path file) throws IOException {
    this.file = file;
    if (Files.exists(file.resolveSibling(file.getFileName() + ".tmp")))
      throw new IOException("Incomplete protection quarantine journal update");
    if (Files.exists(file))
      for (var line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
        if (line.isBlank()) continue;
        try {
          var p = line.split(",", -1);
          if (p.length != 4) throw new IllegalArgumentException("Wrong field count");
          failed.add(
              new BlockKey(
                  UUID.fromString(p[0]),
                  Integer.parseInt(p[1]),
                  Integer.parseInt(p[2]),
                  Integer.parseInt(p[3])));
        } catch (IllegalArgumentException e) {
          throw new IOException("Malformed protection quarantine entry", e);
        }
      }
  }

  public synchronized Set<BlockKey> locations() {
    return Set.copyOf(failed);
  }

  public synchronized void fail(Collection<BlockKey> blocks) throws IOException {
    failed.addAll(blocks);
    save();
  }

  public synchronized void resolve(Collection<BlockKey> blocks) throws IOException {
    var previous = new HashSet<>(failed);
    if (!failed.removeAll(blocks)) return;
    try {
      save();
    } catch (IOException e) {
      failed.clear();
      failed.addAll(previous);
      throw e;
    }
  }

  private void save() throws IOException {
    Files.createDirectories(file.toAbsolutePath().getParent());
    var temporary = file.resolveSibling(file.getFileName() + ".tmp");
    var lines =
        failed.stream()
            .map(b -> b.world() + "," + b.x() + "," + b.y() + "," + b.z())
            .sorted()
            .toList();
    Files.write(temporary, lines, StandardCharsets.UTF_8);
    try {
      Files.move(
          temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
