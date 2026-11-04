package io.github.devkruidenier.landward.storage.sqlite;

import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.model.ClaimedChunk;
import io.github.devkruidenier.landward.model.Land;
import io.github.devkruidenier.landward.model.LandMember;
import io.github.devkruidenier.landward.model.MemberPermissions;
import io.github.devkruidenier.landward.storage.LandStorage;
import io.github.devkruidenier.landward.storage.StorageException;
import io.github.devkruidenier.landward.storage.StorageSnapshot;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SqliteLandStorage implements LandStorage {

    private final String jdbcUrl;

    public SqliteLandStorage(Path databaseFile) {
        this.jdbcUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
    }

    @Override
    public void initialize() throws StorageException {
        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS lands (
                            id TEXT PRIMARY KEY,
                            owner_uuid TEXT NOT NULL UNIQUE,
                            name TEXT NOT NULL COLLATE NOCASE UNIQUE,
                            created_at INTEGER NOT NULL
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS claims (
                            world_uuid TEXT NOT NULL,
                            chunk_x INTEGER NOT NULL,
                            chunk_z INTEGER NOT NULL,
                            land_id TEXT NOT NULL,
                            PRIMARY KEY (world_uuid, chunk_x, chunk_z),
                            FOREIGN KEY (land_id) REFERENCES lands(id) ON DELETE CASCADE
                        )
                        """);
                statement.execute("CREATE INDEX IF NOT EXISTS idx_claims_land_id ON claims(land_id)");
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS land_members (
                            land_id TEXT NOT NULL,
                            player_uuid TEXT NOT NULL,
                            can_build INTEGER NOT NULL,
                            can_containers INTEGER NOT NULL,
                            can_entities INTEGER NOT NULL,
                            PRIMARY KEY (land_id, player_uuid),
                            FOREIGN KEY (land_id) REFERENCES lands(id) ON DELETE CASCADE
                        )
                        """);
            }
        } catch (ClassNotFoundException | SQLException exception) {
            throw new StorageException("Could not initialize SQLite storage", exception);
        }
    }

    @Override
    public StorageSnapshot load() throws StorageException {
        List<Land> lands = new ArrayList<>();
        List<ClaimedChunk> claims = new ArrayList<>();
        List<LandMember> members = new ArrayList<>();
        try (Connection connection = connection()) {
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT id, owner_uuid, name, created_at FROM lands")) {
                while (result.next()) {
                    lands.add(new Land(
                            UUID.fromString(result.getString("id")),
                            UUID.fromString(result.getString("owner_uuid")),
                            result.getString("name"),
                            Instant.ofEpochMilli(result.getLong("created_at"))
                    ));
                }
            }
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT world_uuid, chunk_x, chunk_z, land_id FROM claims"
                 )) {
                while (result.next()) {
                    claims.add(new ClaimedChunk(
                            new ChunkPosition(
                                    UUID.fromString(result.getString("world_uuid")),
                                    result.getInt("chunk_x"),
                                    result.getInt("chunk_z")
                            ),
                            UUID.fromString(result.getString("land_id"))
                    ));
                }
            }
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT land_id, player_uuid, can_build, can_containers, can_entities FROM land_members"
                 )) {
                while (result.next()) {
                    members.add(new LandMember(
                            UUID.fromString(result.getString("land_id")),
                            UUID.fromString(result.getString("player_uuid")),
                            new MemberPermissions(
                                    result.getBoolean("can_build"),
                                    result.getBoolean("can_containers"),
                                    result.getBoolean("can_entities")
                            )
                    ));
                }
            }
            return new StorageSnapshot(List.copyOf(lands), List.copyOf(claims), List.copyOf(members));
        } catch (SQLException | IllegalArgumentException exception) {
            throw new StorageException("Could not load SQLite data", exception);
        }
    }

    @Override
    public void insertLand(Land land) throws StorageException {
        String sql = "INSERT INTO lands(id, owner_uuid, name, created_at) VALUES (?, ?, ?, ?)";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, land.id().toString());
            statement.setString(2, land.ownerId().toString());
            statement.setString(3, land.name());
            statement.setLong(4, land.createdAt().toEpochMilli());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Could not save land", exception);
        }
    }

    @Override
    public void insertLandWithClaim(Land land, ClaimedChunk claim) throws StorageException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO lands(id, owner_uuid, name, created_at) VALUES (?, ?, ?, ?)"
                )) {
                    statement.setString(1, land.id().toString());
                    statement.setString(2, land.ownerId().toString());
                    statement.setString(3, land.name());
                    statement.setLong(4, land.createdAt().toEpochMilli());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO claims(world_uuid, chunk_x, chunk_z, land_id) VALUES (?, ?, ?, ?)"
                )) {
                    statement.setString(1, claim.position().worldId().toString());
                    statement.setInt(2, claim.position().x());
                    statement.setInt(3, claim.position().z());
                    statement.setString(4, claim.landId().toString());
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new StorageException("Could not save land and initial claim", exception);
        }
    }

    @Override
    public void insertClaim(ClaimedChunk claim) throws StorageException {
        String sql = "INSERT INTO claims(world_uuid, chunk_x, chunk_z, land_id) VALUES (?, ?, ?, ?)";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, claim.position().worldId().toString());
            statement.setInt(2, claim.position().x());
            statement.setInt(3, claim.position().z());
            statement.setString(4, claim.landId().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Could not save claim", exception);
        }
    }

    @Override
    public void insertMember(LandMember member) throws StorageException {
        String sql = "INSERT INTO land_members(land_id, player_uuid, can_build, can_containers, can_entities) VALUES (?, ?, ?, ?, ?)";
        writeMember(sql, member, "Could not save land member");
    }

    @Override
    public void updateMember(LandMember member) throws StorageException {
        String sql = "UPDATE land_members SET can_build = ?, can_containers = ?, can_entities = ? WHERE land_id = ? AND player_uuid = ?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, member.permissions().build());
            statement.setBoolean(2, member.permissions().containers());
            statement.setBoolean(3, member.permissions().entities());
            statement.setString(4, member.landId().toString());
            statement.setString(5, member.playerId().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Could not update land member", exception);
        }
    }

    @Override
    public void deleteMember(UUID landId, UUID playerId) throws StorageException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM land_members WHERE land_id = ? AND player_uuid = ?"
        )) {
            statement.setString(1, landId.toString());
            statement.setString(2, playerId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Could not delete land member", exception);
        }
    }

    @Override
    public String type() {
        return "SQLITE";
    }

    private Connection connection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
        }
        return connection;
    }

    private void writeMember(String sql, LandMember member, String failureMessage) throws StorageException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, member.landId().toString());
            statement.setString(2, member.playerId().toString());
            statement.setBoolean(3, member.permissions().build());
            statement.setBoolean(4, member.permissions().containers());
            statement.setBoolean(5, member.permissions().entities());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException(failureMessage, exception);
        }
    }
}
