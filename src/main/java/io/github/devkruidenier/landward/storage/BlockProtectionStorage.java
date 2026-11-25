package io.github.devkruidenier.landward.storage;

import io.github.devkruidenier.landward.blockprotection.*;
import io.github.devkruidenier.landward.model.ClaimedChunk;
import java.sql.*;
import java.util.*;

/** Portable JDBC statements. All callers supply a transaction on the active LandWard backend. */
public final class BlockProtectionStorage {
  private BlockProtectionStorage() {}

  public static void initialize(Connection c) throws SQLException {
    try (var s = c.createStatement()) {
      s.execute(
          "CREATE TABLE IF NOT EXISTS block_protections (id VARCHAR(36) PRIMARY KEY, land_id"
              + " VARCHAR(36) NOT NULL, owner_uuid VARCHAR(36) NOT NULL, material VARCHAR(80) NOT"
              + " NULL, created_at BIGINT NOT NULL, revision BIGINT NOT NULL)");
      s.execute(
          "CREATE TABLE IF NOT EXISTS protected_blocks (world_uuid VARCHAR(36) NOT NULL, block_x"
              + " INT NOT NULL, block_y INT NOT NULL, block_z INT NOT NULL, protection_id"
              + " VARCHAR(36) NOT NULL, PRIMARY KEY(world_uuid, block_x, block_y, block_z))");
      s.execute(
          "CREATE TABLE IF NOT EXISTS protection_members (protection_id VARCHAR(36) NOT NULL,"
              + " player_uuid VARCHAR(36) NOT NULL, PRIMARY KEY(protection_id, player_uuid))");
      s.execute(
          "CREATE TABLE IF NOT EXISTS block_placements (world_uuid VARCHAR(36) NOT NULL, block_x"
              + " INT NOT NULL, block_y INT NOT NULL, block_z INT NOT NULL, land_id VARCHAR(36) NOT"
              + " NULL, owner_uuid VARCHAR(36) NOT NULL, material VARCHAR(80) NOT NULL, generation"
              + " VARCHAR(36) NOT NULL, PRIMARY KEY(world_uuid, block_x, block_y, block_z))");
    }
    index(c, "block_protections", "idx_protection_land_owner", "land_id, owner_uuid");
    index(c, "protected_blocks", "idx_protected_unit", "protection_id");
    index(c, "protection_members", "idx_protection_member", "player_uuid");
    index(c, "block_placements", "idx_placement_land", "land_id");
  }

  private static void index(Connection c, String table, String name, String columns)
      throws SQLException {
    boolean exists = false;
    try (var r = c.getMetaData().getIndexInfo(c.getCatalog(), null, table, false, false)) {
      while (r.next()) if (name.equalsIgnoreCase(r.getString("INDEX_NAME"))) exists = true;
    }
    if (!exists)
      try (var s = c.createStatement()) {
        s.execute("CREATE INDEX " + name + " ON " + table + "(" + columns + ")");
      }
  }

  public static List<BlockProtection> load(Connection c) throws SQLException {
    Map<UUID, List<BlockKey>> positions = new HashMap<>();
    Map<UUID, Set<UUID>> members = new HashMap<>();
    try (var s = c.createStatement();
        var r = s.executeQuery("SELECT * FROM protected_blocks")) {
      while (r.next())
        positions.computeIfAbsent(uuid(r, "protection_id"), k -> new ArrayList<>()).add(key(r));
    }
    try (var s = c.createStatement();
        var r = s.executeQuery("SELECT * FROM protection_members")) {
      while (r.next())
        members
            .computeIfAbsent(uuid(r, "protection_id"), k -> new HashSet<>())
            .add(uuid(r, "player_uuid"));
    }
    List<BlockProtection> result = new ArrayList<>();
    try (var s = c.createStatement();
        var r = s.executeQuery("SELECT * FROM block_protections")) {
      while (r.next()) {
        UUID id = uuid(r, "id");
        var blocks = positions.remove(id);
        if (blocks == null) throw new SQLException("Protection without block locations: " + id);
        result.add(
            new BlockProtection(
                id,
                uuid(r, "land_id"),
                uuid(r, "owner_uuid"),
                r.getString("material"),
                blocks.stream()
                    .sorted(
                        Comparator.comparingInt(BlockKey::x)
                            .thenComparingInt(BlockKey::y)
                            .thenComparingInt(BlockKey::z))
                    .toList(),
                members.getOrDefault(id, Set.of()),
                r.getLong("created_at"),
                r.getLong("revision")));
        members.remove(id);
      }
    }
    if (!positions.isEmpty() || !members.isEmpty())
      throw new SQLException("Orphaned block protection records");
    return List.copyOf(result);
  }

  public static List<BlockPlacement> placements(Connection c) throws SQLException {
    List<BlockPlacement> result = new ArrayList<>();
    try (var s = c.createStatement();
        var r = s.executeQuery("SELECT * FROM block_placements")) {
      while (r.next())
        result.add(
            new BlockPlacement(
                key(r),
                uuid(r, "land_id"),
                uuid(r, "owner_uuid"),
                r.getString("material"),
                uuid(r, "generation")));
    }
    return List.copyOf(result);
  }

  public static void insert(Connection c, BlockProtection p) throws SQLException {
    update(
        c,
        "INSERT INTO block_protections(id, land_id, owner_uuid, material, created_at, revision)"
            + " VALUES(?,?,?,?,?,?)",
        p.id(),
        p.landId(),
        p.owner(),
        p.material(),
        p.created(),
        p.revision());
    for (var b : p.blocks())
      update(
          c,
          "INSERT INTO protected_blocks(world_uuid, block_x, block_y, block_z, protection_id)"
              + " VALUES(?,?,?,?,?)",
          b.world(),
          b.x(),
          b.y(),
          b.z(),
          p.id());
    for (var m : p.shared())
      update(
          c, "INSERT INTO protection_members(protection_id, player_uuid) VALUES(?,?)", p.id(), m);
  }

  public static void save(Connection c, BlockProtection p) throws SQLException {
    delete(c, p.id());
    insert(c, p);
  }

  public static void delete(Connection c, UUID id) throws SQLException {
    update(c, "DELETE FROM protection_members WHERE protection_id = ?", id);
    update(c, "DELETE FROM protected_blocks WHERE protection_id = ?", id);
    update(c, "DELETE FROM block_protections WHERE id = ?", id);
  }

  public static void placement(Connection c, BlockPlacement p) throws SQLException {
    forget(c, p.position());
    var b = p.position();
    update(
        c,
        "INSERT INTO block_placements(world_uuid, block_x, block_y, block_z, land_id, owner_uuid,"
            + " material, generation) VALUES(?,?,?,?,?,?,?,?)",
        b.world(),
        b.x(),
        b.y(),
        b.z(),
        p.landId(),
        p.owner(),
        p.material(),
        p.generation());
  }

  /** Replacing either part invalidates the complete unit, never leaving an exposed orphan half. */
  public static void forget(Connection c, BlockKey b) throws SQLException {
    List<UUID> ids = new ArrayList<>();
    try (var s =
        c.prepareStatement(
            "SELECT protection_id FROM protected_blocks WHERE world_uuid=? AND block_x=? AND"
                + " block_y=? AND block_z=?")) {
      bind(s, b.world(), b.x(), b.y(), b.z());
      try (var r = s.executeQuery()) {
        while (r.next()) ids.add(uuid(r, "protection_id"));
      }
    }
    for (var id : ids) delete(c, id);
    update(
        c,
        "DELETE FROM block_placements WHERE world_uuid=? AND block_x=? AND block_y=? AND block_z=?",
        b.world(),
        b.x(),
        b.y(),
        b.z());
  }

  public static void removeMember(Connection c, UUID land, UUID player) throws SQLException {
    String owned = "SELECT id FROM block_protections WHERE land_id=? AND owner_uuid=?";
    update(
        c, "DELETE FROM protection_members WHERE protection_id IN (" + owned + ")", land, player);
    update(c, "DELETE FROM protected_blocks WHERE protection_id IN (" + owned + ")", land, player);
    update(c, "DELETE FROM block_protections WHERE land_id=? AND owner_uuid=?", land, player);
    update(
        c,
        "DELETE FROM protection_members WHERE player_uuid=? AND protection_id IN (SELECT id FROM"
            + " block_protections WHERE land_id=?)",
        player,
        land);
    update(c, "DELETE FROM block_placements WHERE land_id=? AND owner_uuid=?", land, player);
  }

  public static void removeLand(Connection c, UUID land) throws SQLException {
    update(
        c,
        "DELETE FROM protection_members WHERE protection_id IN (SELECT id FROM block_protections"
            + " WHERE land_id=?)",
        land);
    update(
        c,
        "DELETE FROM protected_blocks WHERE protection_id IN (SELECT id FROM block_protections"
            + " WHERE land_id=?)",
        land);
    update(c, "DELETE FROM block_protections WHERE land_id=?", land);
    update(c, "DELETE FROM block_placements WHERE land_id=?", land);
  }

  public static void removeChunk(Connection c, ClaimedChunk claim) throws SQLException {
    var p = claim.position();
    List<BlockKey> keys = new ArrayList<>();
    try (var s =
        c.prepareStatement(
            "SELECT b.* FROM protected_blocks b JOIN block_protections p ON p.id=b.protection_id"
                + " WHERE p.land_id=? AND b.world_uuid=? AND b.block_x BETWEEN ? AND ? AND"
                + " b.block_z BETWEEN ? AND ?")) {
      bind(
          s, claim.landId(), p.worldId(), p.x() * 16, p.x() * 16 + 15, p.z() * 16, p.z() * 16 + 15);
      try (var r = s.executeQuery()) {
        while (r.next()) keys.add(key(r));
      }
    }
    for (var b : keys) forget(c, b);
    update(
        c,
        "DELETE FROM block_placements WHERE land_id=? AND world_uuid=? AND block_x BETWEEN ? AND ?"
            + " AND block_z BETWEEN ? AND ?",
        claim.landId(),
        p.worldId(),
        p.x() * 16,
        p.x() * 16 + 15,
        p.z() * 16,
        p.z() * 16 + 15);
  }

  public static void clear(Connection c) throws SQLException {
    for (var t :
        List.of("protection_members", "protected_blocks", "block_protections", "block_placements"))
      update(c, "DELETE FROM " + t);
  }

  public static void transfer(Connection c, UUID land, UUID newOwner) throws SQLException {
    UUID old;
    try (var s = c.prepareStatement("SELECT owner_uuid FROM lands WHERE id=?")) {
      bind(s, land);
      try (var r = s.executeQuery()) {
        if (!r.next()) throw new SQLException("Missing transfer land");
        old = uuid(r, "owner_uuid");
      }
    }
    boolean stays;
    try (var s =
        c.prepareStatement(
            "SELECT player_uuid FROM land_members WHERE land_id=? AND player_uuid=?")) {
      bind(s, land, old);
      try (var r = s.executeQuery()) {
        stays = r.next();
      }
    }
    if (!stays) removeMember(c, land, old);
    // The new owner remains eligible; removing their redundant membership must NOT remove their
    // locks.
    update(c, "DELETE FROM land_members WHERE land_id=? AND player_uuid=?", land, newOwner);
    update(c, "UPDATE lands SET owner_uuid=? WHERE id=?", newOwner, land);
  }

  private static List<UUID> ids(Connection c, String sql, Object... args) throws SQLException {
    List<UUID> ids = new ArrayList<>();
    try (var s = c.prepareStatement(sql)) {
      bind(s, args);
      try (var r = s.executeQuery()) {
        while (r.next()) ids.add(UUID.fromString(r.getString(1)));
      }
    }
    return ids;
  }

  private static UUID uuid(ResultSet r, String name) throws SQLException {
    return UUID.fromString(r.getString(name));
  }

  private static BlockKey key(ResultSet r) throws SQLException {
    return new BlockKey(
        uuid(r, "world_uuid"), r.getInt("block_x"), r.getInt("block_y"), r.getInt("block_z"));
  }

  private static void bind(PreparedStatement s, Object... args) throws SQLException {
    for (int i = 0; i < args.length; i++)
      s.setObject(i + 1, args[i] instanceof UUID u ? u.toString() : args[i]);
  }

  private static void update(Connection c, String sql, Object... args) throws SQLException {
    try (var s = c.prepareStatement(sql)) {
      bind(s, args);
      s.executeUpdate();
    }
  }
}
