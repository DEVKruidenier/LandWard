package io.github.devkruidenier.landward.storage;

import io.github.devkruidenier.landward.model.*;
import java.sql.*;
import java.util.*;

/** Portable relational storage shared by SQLite and MySQL. Callers own transactions. */
public final class FeatureStorage {
    private FeatureStorage() {}
    public static void initialize(Connection connection) throws SQLException {
        String suffix = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("sqlite")
                ? "" : " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS land_environment (land_id CHAR(36) PRIMARY KEY, allowed_rules VARCHAR(1000) NOT NULL, FOREIGN KEY (land_id) REFERENCES lands(id) ON DELETE CASCADE)" + suffix);
            statement.execute("CREATE TABLE IF NOT EXISTS land_warps (id CHAR(36) PRIMARY KEY, land_id CHAR(36) NOT NULL, name VARCHAR(96) NOT NULL, world_uuid CHAR(36) NOT NULL, x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL, yaw FLOAT NOT NULL, pitch FLOAT NOT NULL, FOREIGN KEY (land_id) REFERENCES lands(id) ON DELETE CASCADE)" + suffix);
        }
    }
    public static List<LandFeatures> load(Connection connection) throws SQLException {
        Map<UUID, EnvironmentSettings> environments = new LinkedHashMap<>();
        Map<UUID, List<LandWarp>> warps = new HashMap<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT land_id, allowed_rules FROM land_environment")) {
            while (rows.next()) {
                var rules = EnumSet.noneOf(EnvironmentSettings.Rule.class);
                String value = rows.getString(2);
                if (!value.isEmpty()) for (String name : value.split(",")) rules.add(EnvironmentSettings.Rule.valueOf(name));
                environments.put(UUID.fromString(rows.getString(1)), new EnvironmentSettings(rules));
            }
        }
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT id, land_id, name, world_uuid, x, y, z, yaw, pitch FROM land_warps")) {
            while (rows.next()) {
                UUID land = UUID.fromString(rows.getString(2));
                if (!environments.containsKey(land)) throw new SQLException("Warp has no environmental land record");
                warps.computeIfAbsent(land, ignored -> new ArrayList<>()).add(new LandWarp(UUID.fromString(rows.getString(1)), rows.getString(3),
                        UUID.fromString(rows.getString(4)), rows.getDouble(5), rows.getDouble(6), rows.getDouble(7), rows.getFloat(8), rows.getFloat(9)));
            }
        }
        return environments.entrySet().stream().map(entry -> new LandFeatures(entry.getKey(), entry.getValue(),
                warps.getOrDefault(entry.getKey(), List.of()))).toList();
    }
    public static void insert(Connection connection, LandFeatures features) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO land_environment(land_id, allowed_rules) VALUES (?, ?)")) {
            statement.setString(1, features.landId().toString());
            statement.setString(2, features.environment().allowed().stream().map(Enum::name).sorted().collect(java.util.stream.Collectors.joining(",")));
            statement.executeUpdate();
        }
        for (var warp : features.warps()) {
            try (var statement = connection.prepareStatement("INSERT INTO land_warps(id, land_id, name, world_uuid, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, warp.id().toString()); statement.setString(2, features.landId().toString());
                statement.setString(3, warp.name()); statement.setString(4, warp.worldId().toString());
                statement.setDouble(5, warp.x()); statement.setDouble(6, warp.y()); statement.setDouble(7, warp.z());
                statement.setFloat(8, warp.yaw()); statement.setFloat(9, warp.pitch()); statement.executeUpdate();
            }
        }
    }
    public static void delete(Connection connection, UUID land) throws SQLException {
        for (String table : List.of("land_warps", "land_environment")) {
            try (var statement = connection.prepareStatement("DELETE FROM " + table + " WHERE land_id = ?")) {
                statement.setString(1, land.toString()); statement.executeUpdate();
            }
        }
    }
    public static void deleteChunkWarps(Connection connection, ClaimedChunk claim) throws SQLException {
        var ids = new ArrayList<String>();
        try (var query = connection.prepareStatement("SELECT id, world_uuid, x, z FROM land_warps WHERE land_id = ?")) {
            query.setString(1, claim.landId().toString());
            try (var rows = query.executeQuery()) {
                while (rows.next()) if (new ChunkPosition(UUID.fromString(rows.getString(2)),
                        ((int)Math.floor(rows.getDouble(3))) >> 4, ((int)Math.floor(rows.getDouble(4))) >> 4).equals(claim.position())) ids.add(rows.getString(1));
            }
        }
        for (String id : ids) try (var statement = connection.prepareStatement("DELETE FROM land_warps WHERE id = ?")) {
            statement.setString(1, id); statement.executeUpdate();
        }
    }
}
