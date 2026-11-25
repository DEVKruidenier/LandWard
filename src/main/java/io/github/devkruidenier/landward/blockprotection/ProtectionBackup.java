package io.github.devkruidenier.landward.blockprotection;

import io.github.devkruidenier.landward.storage.*;
import java.io.*;
import java.util.*;

/** Extension of the versioned migration backup, including placement generations. */
public final class ProtectionBackup {
  private ProtectionBackup() {}

  public record Data(List<BlockProtection> locks, List<BlockPlacement> placements) {}

  public static void write(DataOutputStream out, StorageSnapshot snapshot) throws IOException {
    out.writeInt(snapshot.protections().size());
    for (var p : snapshot.protections()) {
      uuid(out, p.id());
      uuid(out, p.landId());
      uuid(out, p.owner());
      out.writeUTF(p.material());
      out.writeLong(p.created());
      out.writeLong(p.revision());
      out.writeInt(p.blocks().size());
      for (var b : p.blocks()) key(out, b);
      out.writeInt(p.shared().size());
      for (var m : p.shared()) uuid(out, m);
    }
    out.writeInt(snapshot.placements().size());
    for (var p : snapshot.placements()) {
      key(out, p.position());
      uuid(out, p.landId());
      uuid(out, p.owner());
      out.writeUTF(p.material());
      uuid(out, p.generation());
    }
  }

  public static Data read(DataInputStream in) throws IOException {
    var locks = new ArrayList<BlockProtection>();
    var placements = new ArrayList<BlockPlacement>();
    for (int i = count(in); i > 0; i--) {
      UUID id = uuid(in), land = uuid(in), owner = uuid(in);
      String material = in.readUTF();
      long created = in.readLong(), revision = in.readLong();
      int n = count(in);
      if (n < 1 || n > 2) throw new IOException("Invalid protection unit size");
      var blocks = new ArrayList<BlockKey>();
      for (int k = 0; k < n; k++) blocks.add(key(in));
      var members = new HashSet<UUID>();
      for (int k = count(in); k > 0; k--) members.add(uuid(in));
      locks.add(new BlockProtection(id, land, owner, material, blocks, members, created, revision));
    }
    for (int i = count(in); i > 0; i--)
      placements.add(new BlockPlacement(key(in), uuid(in), uuid(in), in.readUTF(), uuid(in)));
    return new Data(List.copyOf(locks), List.copyOf(placements));
  }

  public static void validate(StorageSnapshot s) throws StorageException {
    var ids = new HashSet<UUID>();
    var locations = new HashSet<BlockKey>();
    var placed = new HashSet<BlockKey>();
    for (var p : s.protections()) {
      if (!ids.add(p.id()) || !eligible(s, p.landId(), p.owner()))
        throw new StorageException("Invalid personal protection owner/land: " + p.id(), null);
      for (var b : p.blocks())
        if (!locations.add(b) || !claimed(s, p.landId(), b))
          throw new StorageException("Invalid or duplicate protected location: " + b, null);
      for (var m : p.shared())
        if (!eligible(s, p.landId(), m))
          throw new StorageException("Ineligible shared protection member: " + m, null);
    }
    for (var p : s.placements())
      if (!placed.add(p.position())
          || !eligible(s, p.landId(), p.owner())
          || !claimed(s, p.landId(), p.position()))
        throw new StorageException("Invalid block placement record: " + p.position(), null);
  }

  private static boolean eligible(StorageSnapshot s, UUID land, UUID owner) {
    return !s.adminLandIds().contains(land)
        && s.lands().stream()
            .anyMatch(l ->
                l.id().equals(land)
                    && (l.ownerId().equals(owner)
                        || s.members().stream()
                            .anyMatch(m -> m.landId().equals(land) && m.playerId().equals(owner))));
  }

  private static boolean claimed(StorageSnapshot s, UUID land, BlockKey b) {
    return s.claims().stream()
        .anyMatch(c -> c.landId().equals(land) && c.position().equals(b.chunk()));
  }

  private static int count(DataInputStream in) throws IOException {
    int n = in.readInt();
    if (n < 0 || n > 10_000_000) throw new IOException("Invalid protection backup count");
    return n;
  }

  private static UUID uuid(DataInputStream in) throws IOException {
    return new UUID(in.readLong(), in.readLong());
  }

  private static void uuid(DataOutputStream out, UUID id) throws IOException {
    out.writeLong(id.getMostSignificantBits());
    out.writeLong(id.getLeastSignificantBits());
  }

  private static void key(DataOutputStream out, BlockKey b) throws IOException {
    uuid(out, b.world());
    out.writeInt(b.x());
    out.writeInt(b.y());
    out.writeInt(b.z());
  }

  private static BlockKey key(DataInputStream in) throws IOException {
    return new BlockKey(uuid(in), in.readInt(), in.readInt(), in.readInt());
  }
}
