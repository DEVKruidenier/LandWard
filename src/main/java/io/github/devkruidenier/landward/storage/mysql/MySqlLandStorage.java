package io.github.devkruidenier.landward.storage.mysql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.model.ClaimedChunk;
import io.github.devkruidenier.landward.model.Land;
import io.github.devkruidenier.landward.model.LandMember;
import io.github.devkruidenier.landward.model.MemberPermissions;
import io.github.devkruidenier.landward.model.LandProtectionSettings;
import io.github.devkruidenier.landward.storage.LandStorage;
import io.github.devkruidenier.landward.storage.StorageException;
import io.github.devkruidenier.landward.storage.StorageSnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** MySQL/MariaDB-backed storage. All statements are parameterised and connection pooled. */
public final class MySqlLandStorage implements LandStorage {

    private static final String LAND_INSERT = "INSERT INTO lands(id, owner_uuid, name, created_at, member_build, member_containers, member_entities, member_pvp, allow_hostile_mob_kills, allow_passive_mob_kills, allow_hostile_mob_spawns, allow_doors, allow_interactions) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final boolean ssl;
    private final int poolSize;
    private HikariDataSource dataSource;

    public MySqlLandStorage(String host, int port, String database, String username, String password, boolean ssl, int poolSize) {
        this.host = requireText(host, "host");
        this.port = validatePort(port);
        this.database = requireText(database, "database");
        this.username = requireText(username, "username");
        this.password = password == null ? "" : password;
        this.ssl = ssl;
        this.poolSize = Math.max(1, poolSize);
    }

    @Override
    public void initialize() throws StorageException {
        if (dataSource != null) {
            return;
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("LandWard-MySQL");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&useSSL=" + ssl
                + "&serverTimezone=UTC");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(Math.min(2, poolSize));
        config.setConnectionTimeout(10_000);
        config.setValidationTimeout(5_000);
        config.setInitializationFailTimeout(10_000);

        try {
            dataSource = new HikariDataSource(config);
            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS lands (
                            id CHAR(36) PRIMARY KEY,
                            owner_uuid CHAR(36) NOT NULL UNIQUE,
                            name VARCHAR(64) NOT NULL UNIQUE,
                            created_at BIGINT NOT NULL,
                            member_build BOOLEAN NOT NULL DEFAULT TRUE,
                            member_containers BOOLEAN NOT NULL DEFAULT TRUE,
                            member_entities BOOLEAN NOT NULL DEFAULT TRUE,
                            member_pvp BOOLEAN NOT NULL DEFAULT FALSE,
                            allow_hostile_mob_kills BOOLEAN NOT NULL DEFAULT TRUE,
                            allow_passive_mob_kills BOOLEAN NOT NULL DEFAULT TRUE,
                            allow_hostile_mob_spawns BOOLEAN NOT NULL DEFAULT TRUE,
                            allow_doors BOOLEAN NOT NULL DEFAULT TRUE,
                            allow_interactions BOOLEAN NOT NULL DEFAULT TRUE
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
                statement.execute("ALTER TABLE lands ADD COLUMN IF NOT EXISTS member_build BOOLEAN NOT NULL DEFAULT TRUE");
                statement.execute("ALTER TABLE lands ADD COLUMN IF NOT EXISTS member_containers BOOLEAN NOT NULL DEFAULT TRUE");
                statement.execute("ALTER TABLE lands ADD COLUMN IF NOT EXISTS member_entities BOOLEAN NOT NULL DEFAULT TRUE");
                statement.execute("ALTER TABLE lands ADD COLUMN IF NOT EXISTS member_pvp BOOLEAN NOT NULL DEFAULT FALSE");
                statement.execute("ALTER TABLE lands ADD COLUMN IF NOT EXISTS allow_hostile_mob_kills BOOLEAN NOT NULL DEFAULT TRUE");
                statement.execute("ALTER TABLE lands ADD COLUMN IF NOT EXISTS allow_passive_mob_kills BOOLEAN NOT NULL DEFAULT TRUE");
                statement.execute("ALTER TABLE lands ADD COLUMN IF NOT EXISTS allow_hostile_mob_spawns BOOLEAN NOT NULL DEFAULT TRUE");
                statement.execute("ALTER TABLE lands ADD COLUMN IF NOT EXISTS allow_doors BOOLEAN NOT NULL DEFAULT TRUE");
                statement.execute("ALTER TABLE lands ADD COLUMN IF NOT EXISTS allow_interactions BOOLEAN NOT NULL DEFAULT TRUE");
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS claims (
                            world_uuid CHAR(36) NOT NULL,
                            chunk_x INT NOT NULL,
                            chunk_z INT NOT NULL,
                            land_id CHAR(36) NOT NULL,
                            PRIMARY KEY (world_uuid, chunk_x, chunk_z),
                            INDEX idx_claims_land_id (land_id),
                            CONSTRAINT fk_claims_land FOREIGN KEY (land_id) REFERENCES lands(id) ON DELETE CASCADE
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS admin_lands (
                            land_id CHAR(36) PRIMARY KEY,
                            CONSTRAINT fk_admin_lands_land FOREIGN KEY (land_id) REFERENCES lands(id) ON DELETE CASCADE
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS land_members (
                            land_id CHAR(36) NOT NULL,
                            player_uuid CHAR(36) NOT NULL,
                            can_build BOOLEAN NOT NULL,
                            can_containers BOOLEAN NOT NULL,
                            can_entities BOOLEAN NOT NULL,
                            PRIMARY KEY (land_id, player_uuid),
                            CONSTRAINT fk_members_land FOREIGN KEY (land_id) REFERENCES lands(id) ON DELETE CASCADE
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
            }
        } catch (SQLException | RuntimeException exception) {
            closeQuietly();
            throw new StorageException("Could not initialize MySQL storage", exception);
        }
    }

    @Override
    public StorageSnapshot load() throws StorageException {
        List<Land> lands = new ArrayList<>();
        List<ClaimedChunk> claims = new ArrayList<>();
        List<LandMember> members = new ArrayList<>();
        List<UUID> adminLandIds = new ArrayList<>();
        try (Connection connection = connection()) {
            try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT id, owner_uuid, name, created_at, member_build, member_containers, member_entities, member_pvp, allow_hostile_mob_kills, allow_passive_mob_kills, allow_hostile_mob_spawns, allow_doors, allow_interactions FROM lands")) {
                while (result.next()) {
                    lands.add(new Land(UUID.fromString(result.getString("id")), UUID.fromString(result.getString("owner_uuid")),
                            result.getString("name"), Instant.ofEpochMilli(result.getLong("created_at")), new MemberPermissions(result.getBoolean("member_build"), result.getBoolean("member_containers"), result.getBoolean("member_entities")), new LandProtectionSettings(result.getBoolean("member_pvp"), result.getBoolean("allow_hostile_mob_kills"), result.getBoolean("allow_passive_mob_kills"), result.getBoolean("allow_hostile_mob_spawns"), result.getBoolean("allow_doors"), result.getBoolean("allow_interactions"))));
                }
            }
            try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT land_id FROM admin_lands")) {
                while (result.next()) adminLandIds.add(UUID.fromString(result.getString("land_id")));
            }
            try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT world_uuid, chunk_x, chunk_z, land_id FROM claims")) {
                while (result.next()) {
                    claims.add(new ClaimedChunk(new ChunkPosition(UUID.fromString(result.getString("world_uuid")), result.getInt("chunk_x"), result.getInt("chunk_z")),
                            UUID.fromString(result.getString("land_id"))));
                }
            }
            try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT land_id, player_uuid, can_build, can_containers, can_entities FROM land_members")) {
                while (result.next()) {
                    members.add(new LandMember(UUID.fromString(result.getString("land_id")), UUID.fromString(result.getString("player_uuid")),
                            new MemberPermissions(result.getBoolean("can_build"), result.getBoolean("can_containers"), result.getBoolean("can_entities"))));
                }
            }
            return new StorageSnapshot(List.copyOf(lands), List.copyOf(claims), List.copyOf(members), List.copyOf(adminLandIds));
        } catch (SQLException | IllegalArgumentException exception) {
            throw new StorageException("Could not load MySQL data", exception);
        }
    }

    @Override
    public void importSnapshotAtomically(StorageSnapshot snapshot) throws StorageException {
        writeSnapshotAtomically(snapshot, false);
    }

    @Override
    public void clearSnapshotAtomically() throws StorageException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try { clearSnapshot(connection); connection.commit(); }
            catch (SQLException exception) { connection.rollback(); throw exception; }
        } catch (SQLException exception) { throw new StorageException("Could not clear MySQL snapshot", exception); }
    }

    private void writeSnapshotAtomically(StorageSnapshot snapshot, boolean replace) throws StorageException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                for (Land land : snapshot.lands()) insertLand(connection, land);
                for (UUID landId : snapshot.adminLandIds()) {
                    try (PreparedStatement statement = connection.prepareStatement("INSERT INTO admin_lands(land_id) VALUES (?)")) {
                        statement.setString(1, landId.toString()); statement.executeUpdate();
                    }
                }
                for (ClaimedChunk claim : snapshot.claims()) insertClaim(connection, claim);
                for (LandMember member : snapshot.members()) insertMember(connection, member);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) { throw new StorageException("Could not atomically import MySQL snapshot", exception); }
    }

    private void clearSnapshot(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM land_members");
            statement.executeUpdate("DELETE FROM claims");
            statement.executeUpdate("DELETE FROM admin_lands");
            statement.executeUpdate("DELETE FROM lands");
        }
    }

    @Override public void insertLand(Land land) throws StorageException { executeLand(LAND_INSERT, land, "Could not save land"); }

    @Override
    public void insertLandWithClaim(Land land, ClaimedChunk claim) throws StorageException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(LAND_INSERT)) { bindLand(statement, land); statement.executeUpdate(); }
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO claims(world_uuid, chunk_x, chunk_z, land_id) VALUES (?, ?, ?, ?)")) { bindClaim(statement, claim); statement.executeUpdate(); }
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
    public void insertAdminLandWithClaim(Land land, ClaimedChunk claim) throws StorageException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(LAND_INSERT)) { bindLand(statement, land); statement.executeUpdate(); }
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO claims(world_uuid, chunk_x, chunk_z, land_id) VALUES (?, ?, ?, ?)")) { bindClaim(statement, claim); statement.executeUpdate(); }
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO admin_lands(land_id) VALUES (?)")) { statement.setString(1, land.id().toString()); statement.executeUpdate(); }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new StorageException("Could not save administrative land and initial claim", exception);
        }
    }

    @Override
    public void insertClaim(ClaimedChunk claim) throws StorageException { executeClaim("INSERT INTO claims(world_uuid, chunk_x, chunk_z, land_id) VALUES (?, ?, ?, ?)", claim, "Could not save claim"); }

    @Override
    public void updateLandName(UUID landId, String name) throws StorageException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement("UPDATE lands SET name = ? WHERE id = ?")) {
            statement.setString(1, name); statement.setString(2, landId.toString()); statement.executeUpdate();
        } catch (SQLException exception) { throw new StorageException("Could not rename land", exception); }
    }

    @Override
    public void updateLandOwner(UUID landId, UUID ownerId) throws StorageException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement("UPDATE lands SET owner_uuid = ? WHERE id = ?")) {
            statement.setString(1, ownerId.toString()); statement.setString(2, landId.toString()); statement.executeUpdate();
        } catch (SQLException exception) { throw new StorageException("Could not change land owner", exception); }
    }

    @Override public void updateLandMemberPermissions(UUID landId, MemberPermissions permissions) throws StorageException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement("UPDATE lands SET member_build = ?, member_containers = ?, member_entities = ? WHERE id = ?")) {
            statement.setBoolean(1, permissions.build()); statement.setBoolean(2, permissions.containers()); statement.setBoolean(3, permissions.entities()); statement.setString(4, landId.toString()); statement.executeUpdate();
        } catch (SQLException exception) { throw new StorageException("Could not update land member permissions", exception); }
    }
    @Override public void updateAllMemberPermissions(UUID landId, MemberPermissions permissions) throws StorageException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement("UPDATE land_members SET can_build = ?, can_containers = ?, can_entities = ? WHERE land_id = ?")) {
            statement.setBoolean(1, permissions.build()); statement.setBoolean(2, permissions.containers()); statement.setBoolean(3, permissions.entities()); statement.setString(4, landId.toString()); statement.executeUpdate();
        } catch (SQLException exception) { throw new StorageException("Could not update all land members", exception); }
    }
    @Override public void updateLandProtectionSettings(UUID landId, LandProtectionSettings settings) throws StorageException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement("UPDATE lands SET member_pvp = ?, allow_hostile_mob_kills = ?, allow_passive_mob_kills = ?, allow_hostile_mob_spawns = ?, allow_doors = ?, allow_interactions = ? WHERE id = ?")) {
            bindProtectionSettings(statement, settings, 1); statement.setString(7, landId.toString()); statement.executeUpdate();
        } catch (SQLException exception) { throw new StorageException("Could not update land protection settings", exception); }
    }

    @Override
    public void deleteClaim(ClaimedChunk claim) throws StorageException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement("DELETE FROM claims WHERE world_uuid = ? AND chunk_x = ? AND chunk_z = ? AND land_id = ?")) {
            bindClaim(statement, claim); statement.executeUpdate();
        } catch (SQLException exception) { throw new StorageException("Could not delete claim", exception); }
    }

    @Override
    public void deleteLand(UUID landId) throws StorageException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                deleteLandRecords(connection, landId);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) { throw new StorageException("Could not delete land", exception); }
    }

    @Override
    public void insertMember(LandMember member) throws StorageException { writeMember("INSERT INTO land_members(land_id, player_uuid, can_build, can_containers, can_entities) VALUES (?, ?, ?, ?, ?)", member, false); }

    @Override
    public void updateMember(LandMember member) throws StorageException { writeMember("UPDATE land_members SET can_build = ?, can_containers = ?, can_entities = ? WHERE land_id = ? AND player_uuid = ?", member, true); }

    @Override
    public void deleteMember(UUID landId, UUID playerId) throws StorageException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement("DELETE FROM land_members WHERE land_id = ? AND player_uuid = ?")) {
            statement.setString(1, landId.toString()); statement.setString(2, playerId.toString()); statement.executeUpdate();
        } catch (SQLException exception) { throw new StorageException("Could not delete land member", exception); }
    }

    @Override public String type() { return "MYSQL"; }
    @Override public void close() { closeQuietly(); }

    private Connection connection() throws SQLException {
        if (dataSource == null) throw new SQLException("MySQL storage has not been initialized");
        return dataSource.getConnection();
    }
    private void executeLand(String sql, Land land, String message) throws StorageException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) { bindLand(statement, land); statement.executeUpdate(); }
        catch (SQLException exception) { throw new StorageException(message, exception); }
    }
    private void executeClaim(String sql, ClaimedChunk claim, String message) throws StorageException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) { bindClaim(statement, claim); statement.executeUpdate(); }
        catch (SQLException exception) { throw new StorageException(message, exception); }
    }
    private static void bindLand(PreparedStatement statement, Land land) throws SQLException {
        statement.setString(1, land.id().toString()); statement.setString(2, land.ownerId().toString()); statement.setString(3, land.name()); statement.setLong(4, land.createdAt().toEpochMilli());
        statement.setBoolean(5, land.memberPermissions().build()); statement.setBoolean(6, land.memberPermissions().containers()); statement.setBoolean(7, land.memberPermissions().entities());
        bindProtectionSettings(statement, land.protectionSettings(), 8);
    }
    private static void bindProtectionSettings(PreparedStatement statement, LandProtectionSettings settings, int offset) throws SQLException {
        statement.setBoolean(offset, settings.memberPvp()); statement.setBoolean(offset + 1, settings.hostileMobKills());
        statement.setBoolean(offset + 2, settings.passiveMobKills()); statement.setBoolean(offset + 3, settings.hostileMobSpawns());
        statement.setBoolean(offset + 4, settings.doors()); statement.setBoolean(offset + 5, settings.interactions());
    }
    private static void bindClaim(PreparedStatement statement, ClaimedChunk claim) throws SQLException {
        statement.setString(1, claim.position().worldId().toString()); statement.setInt(2, claim.position().x()); statement.setInt(3, claim.position().z()); statement.setString(4, claim.landId().toString());
    }
    private static void insertLand(Connection connection, Land land) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LAND_INSERT)) { bindLand(statement, land); statement.executeUpdate(); }
    }
    private static void deleteLandRecords(Connection connection, UUID landId) throws SQLException {
        String id = landId.toString();
        for (String table : List.of("land_members", "claims", "admin_lands", "lands")) {
            String column = table.equals("lands") ? "id" : "land_id";
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE " + column + " = ?")) {
                statement.setString(1, id);
                statement.executeUpdate();
            }
        }
    }
    private static void insertClaim(Connection connection, ClaimedChunk claim) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO claims(world_uuid, chunk_x, chunk_z, land_id) VALUES (?, ?, ?, ?)")) { bindClaim(statement, claim); statement.executeUpdate(); }
    }
    private static void insertMember(Connection connection, LandMember member) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO land_members(land_id, player_uuid, can_build, can_containers, can_entities) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, member.landId().toString()); statement.setString(2, member.playerId().toString());
            statement.setBoolean(3, member.permissions().build()); statement.setBoolean(4, member.permissions().containers()); statement.setBoolean(5, member.permissions().entities()); statement.executeUpdate();
        }
    }
    private void writeMember(String sql, LandMember member, boolean update) throws StorageException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            if (update) { statement.setBoolean(1, member.permissions().build()); statement.setBoolean(2, member.permissions().containers()); statement.setBoolean(3, member.permissions().entities()); statement.setString(4, member.landId().toString()); statement.setString(5, member.playerId().toString()); }
            else { statement.setString(1, member.landId().toString()); statement.setString(2, member.playerId().toString()); statement.setBoolean(3, member.permissions().build()); statement.setBoolean(4, member.permissions().containers()); statement.setBoolean(5, member.permissions().entities()); }
            statement.executeUpdate();
        } catch (SQLException exception) { throw new StorageException(update ? "Could not update land member" : "Could not save land member", exception); }
    }
    private void closeQuietly() { if (dataSource != null) { dataSource.close(); dataSource = null; } }
    private static String requireText(String value, String field) { if (value == null || value.isBlank()) throw new IllegalArgumentException("MySQL " + field + " must not be empty"); return value; }
    private static int validatePort(int value) { if (value < 1 || value > 65535) throw new IllegalArgumentException("MySQL port must be between 1 and 65535"); return value; }
}
