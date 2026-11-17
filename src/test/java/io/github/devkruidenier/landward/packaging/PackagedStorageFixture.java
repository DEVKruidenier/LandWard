package io.github.devkruidenier.landward.packaging;

import io.github.devkruidenier.landward.model.*;
import io.github.devkruidenier.landward.storage.*;
import io.github.devkruidenier.landward.storage.migration.StorageMigrationService;
import io.github.devkruidenier.landward.storage.mysql.MySqlLandStorage;
import io.github.devkruidenier.landward.storage.sqlite.SqliteLandStorage;

import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.jar.JarFile;

/** Runs with the distributable and explicitly modelled server JDBC/SLF4J libraries. */
public final class PackagedStorageFixture {
    public static void main(String[] args) throws Exception {
        Path artifact = Path.of(args[0]);
        check(Files.size(artifact) < 15_000_000L, "Universal JAR exceeds the conservative Hangar upload budget");
        Path directory = Files.createTempDirectory(Files.createDirectories(Path.of(args[2])), "probe-");
        verifyArchive(artifact, Path.of(args[1]));
        for (String name : List.of("io.github.devkruidenier.landward.libs.hikari.HikariDataSource",
                "net.kyori.adventure.text.Component")) {
            Class<?> type = Class.forName(name);
            check(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).equals(artifact),
                    "Dependency escaped packaged JAR: " + name);
        }
        for (String name : List.of("org.sqlite.JDBC", "com.mysql.cj.jdbc.Driver")) {
            Class<?> type = Class.forName(name);
            check(!Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).equals(artifact),
                    "Driver must come from the server: " + name);
            System.out.println("Server driver: " + name + " " + type.getPackage().getImplementationVersion());
        }
        Set<String> drivers = new HashSet<>();
        ServiceLoader.load(Driver.class).forEach(driver -> drivers.add(driver.getClass().getName()));
        check(drivers.containsAll(Set.of("org.sqlite.JDBC", "com.mysql.cj.jdbc.Driver")),
                "Missing JDBC ServiceLoader provider: " + drivers);
        // No Class.forName is necessary to register either JDBC driver.
        check(DriverManager.getDriver("jdbc:mysql://localhost/test").getClass().getName().equals("com.mysql.cj.jdbc.Driver"),
                "MySQL driver discovery failed");
        Path database = directory.resolve("source.db");
        StorageSnapshot expected = snapshot();
        try (SqliteLandStorage sqlite = new SqliteLandStorage(database)) {
            sqlite.initialize();
            sqlite.importSnapshotAtomically(expected);
            equal(expected, sqlite.load());
            sqlite.updateLandName(expected.lands().getFirst().id(), "Rotterdam");
            check(sqlite.load().lands().stream().anyMatch(land -> land.name().equals("Rotterdam")), "Rename failed");
            expected = sqlite.load();
        }
        try (SqliteLandStorage sqlite = new SqliteLandStorage(database)) {
            sqlite.initialize();
            equal(expected, sqlite.load());
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA integrity_check")) {
            check(result.next() && result.getString(1).equals("ok"), "SQLite integrity check failed");
        }
        check(System.getProperty("org.sqlite.lib.path") == null, "Probe must use bundled native extraction");
        System.out.println("PASS packaged SQLite: native extraction, create/import, rename, reopen, integrity");
        if (Boolean.parseBoolean(System.getenv("LANDWARD_MYSQL_INTEGRATION"))) {
            verifyMySql(directory, database, expected);
        } else {
            System.out.println("SKIP live MySQL/MariaDB: LANDWARD_MYSQL_INTEGRATION is not enabled");
        }
        System.out.println("PASS packaged dependency isolation and both JDBC service providers");
    }

    private static void verifyArchive(Path artifact, Path sqliteDependency) throws Exception {
        try (JarFile jar = new JarFile(artifact.toFile())) {
            Set<String> names = new HashSet<>();
            for (var entry : jar.stream().toList()) {
                check(names.add(entry.getName()), "Duplicate ZIP entry: " + entry.getName());
                check(!entry.getName().contains("PackagedStorageFixture"), "Test fixture shipped in release");
            }
            check(Path.of(Class.forName("org.sqlite.JDBC").getProtectionDomain().getCodeSource().getLocation().toURI())
                    .equals(sqliteDependency), "Wrong simulated server SQLite dependency");
            check(names.stream().noneMatch(n -> n.startsWith("org/sqlite/") || n.startsWith("com/mysql/")),
                    "Server JDBC libraries must not be duplicated in the plugin");
            for (String resource : List.of("plugin.yml", "config.yml", "META-INF/LICENSE-LandWard.txt",
                    "META-INF/THIRD_PARTY_NOTICES.md")) {
                check(names.contains(resource), "Missing metadata/license: " + resource);
            }
            check(names.stream().filter(n -> n.startsWith("lang/") && n.endsWith(".yml")).count() == 13,
                    "A language catalog is missing");
            for (String prefix : List.of("com/mysql/cj/xdevapi/", "com/mysql/cj/protocol/x/", "com/mysql/cj/x/protobuf/")) {
                check(names.stream().noneMatch(n -> n.startsWith(prefix)), "Unused X API retained: " + prefix);
            }
            for (String type : List.of("com/mysql/cj/protocol/ExportControlled.class",
                    "com/mysql/cj/CharsetMapping.class", "com/mysql/cj/protocol/a/authentication/CachingSha2PasswordPlugin.class",
                    "com/mysql/cj/protocol/a/authentication/MysqlNativePasswordPlugin.class")) {
                Class.forName(type.replace('/', '.').replace(".class", ""));
            }
            System.out.println("PASS archive: no duplicate server drivers; languages, JDBC security classes and notices available");
        }
    }

    private static void verifyMySql(Path directory, Path database, StorageSnapshot expected) throws Exception {
        String url = "jdbc:mysql://" + env("LANDWARD_MYSQL_HOST", "127.0.0.1") + ":"
                + env("LANDWARD_MYSQL_PORT", "3306") + "/" + required("LANDWARD_MYSQL_DATABASE")
                + "?useSSL=" + env("LANDWARD_MYSQL_SSL", "false");
        UUID legacyLand = null;
        try (Connection connection = DriverManager.getConnection(url, required("LANDWARD_MYSQL_USER"), required("LANDWARD_MYSQL_PASSWORD"));
             ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), null, "lands", null)) {
            if (!tables.next()) {
                // A fresh dedicated test database exercises the actual old-schema
                // upgrade and verifies that its existing data survives the DDL.
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE lands (id CHAR(36) PRIMARY KEY, owner_uuid CHAR(36) NOT NULL UNIQUE, "
                            + "name VARCHAR(64) NOT NULL UNIQUE, created_at BIGINT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                }
                legacyLand = UUID.randomUUID();
                try (var insert = connection.prepareStatement("INSERT INTO lands VALUES (?, ?, ?, ?)")) {
                    insert.setString(1, legacyLand.toString()); insert.setString(2, UUID.randomUUID().toString());
                    insert.setString(3, "Legacy land"); insert.setLong(4, 1700000000000L); insert.executeUpdate();
                }
            }
        }
        if (Boolean.parseBoolean(env("LANDWARD_MYSQL_SSL", "false"))) {
            try (Connection connection = DriverManager.getConnection(url, required("LANDWARD_MYSQL_USER"), required("LANDWARD_MYSQL_PASSWORD"));
                 Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SHOW SESSION STATUS LIKE 'Ssl_cipher'")) {
                check(result.next() && !result.getString(2).isBlank(), "TLS was requested but is not active");
            }
            System.out.println("PASS packaged JDBC TLS negotiation");
        }
        try (MySqlLandStorage mysql = new MySqlLandStorage(env("LANDWARD_MYSQL_HOST", "127.0.0.1"),
                Integer.parseInt(env("LANDWARD_MYSQL_PORT", "3306")), required("LANDWARD_MYSQL_DATABASE"),
                required("LANDWARD_MYSQL_USER"), required("LANDWARD_MYSQL_PASSWORD"),
                Boolean.parseBoolean(env("LANDWARD_MYSQL_SSL", "false")), 2);
             SqliteLandStorage source = new SqliteLandStorage(database);
             SqliteLandStorage returned = new SqliteLandStorage(directory.resolve("returned.db"))) {
            mysql.initialize();
            if (legacyLand != null) {
                UUID id = legacyLand;
                Land upgraded = mysql.load().lands().stream().filter(land -> land.id().equals(id)).findFirst().orElseThrow();
                check(upgraded.name().equals("Legacy land") && upgraded.memberPermissions().equals(MemberPermissions.FULL)
                        && upgraded.protectionSettings().equals(LandProtectionSettings.DEFAULT), "Legacy schema upgrade lost data/defaults");
                mysql.deleteLand(legacyLand);
                System.out.println("PASS legacy SQL schema upgrade with existing land retained");
            }
            var before = mysql.load();
            check(before.lands().isEmpty() && before.claims().isEmpty() && before.members().isEmpty(),
                    "Use a dedicated EMPTY integration database; existing data will never be cleared");
            StorageMigrationService migration = new StorageMigrationService(directory.resolve("backups"));
            migration.migrate(source, mysql);
            equal(expected, mysql.load());
            mysql.updateLandName(expected.lands().getFirst().id(), "Utrecht");
            check(mysql.load().lands().stream().anyMatch(land -> land.name().equals("Utrecht")), "Pooled JDBC rename failed");
            migration.migrate(mysql, returned);
            equal(mysql.load(), returned.load());
            mysql.clearSnapshotAtomically();
            check(mysql.load().lands().isEmpty(), "Integration cleanup failed");
        }
        System.out.println("PASS packaged MySQL/MariaDB: pooling, schema, UTF-8, SQLite -> MySQL -> SQLite migration");
    }

    private static StorageSnapshot snapshot() {
        UUID playerLand = UUID.randomUUID(); UUID adminLand = UUID.randomUUID(); UUID world = UUID.randomUUID();
        Instant now = Instant.ofEpochMilli(System.currentTimeMillis());
        return new StorageSnapshot(List.of(
                new Land(playerLand, UUID.randomUUID(), "Amsterdam", now, MemberPermissions.FULL, LandProtectionSettings.DEFAULT),
                new Land(adminLand, UUID.randomUUID(), "Veilige Zone Één", now, MemberPermissions.FULL, LandProtectionSettings.DEFAULT)),
                List.of(new ClaimedChunk(new ChunkPosition(world, -32, -9), playerLand),
                        new ClaimedChunk(new ChunkPosition(world, -31, -9), playerLand),
                        new ClaimedChunk(new ChunkPosition(world, 0, 0), adminLand)),
                List.of(new LandMember(playerLand, UUID.randomUUID(), new MemberPermissions(true, false, true))), List.of(adminLand),
                List.of(new io.github.devkruidenier.landward.model.LandFeatures(playerLand,
                        io.github.devkruidenier.landward.model.EnvironmentSettings.defaults(false),
                        List.of(new io.github.devkruidenier.landward.model.LandWarp(UUID.randomUUID(), "Thuis Één", world,
                                -500.25, 81.5, -130.75, 137.5F, -22.5F))),
                        new io.github.devkruidenier.landward.model.LandFeatures(adminLand,
                                io.github.devkruidenier.landward.model.EnvironmentSettings.defaults(true), List.of())));
    }

    private static void equal(StorageSnapshot expected, StorageSnapshot actual) {
        check(Set.copyOf(expected.lands()).equals(Set.copyOf(actual.lands())), "Land persistence mismatch");
        check(Set.copyOf(expected.claims()).equals(Set.copyOf(actual.claims())), "Chunk persistence mismatch");
        check(Set.copyOf(expected.members()).equals(Set.copyOf(actual.members())), "Member persistence mismatch");
        check(Set.copyOf(expected.adminLandIds()).equals(Set.copyOf(actual.adminLandIds())), "Safe Zone persistence mismatch");
        check(Set.copyOf(expected.features()).equals(Set.copyOf(actual.features())), "Environment/warp persistence mismatch");
    }
    private static String env(String key, String fallback) { return System.getenv().getOrDefault(key, fallback); }
    private static String required(String key) { String value = System.getenv(key); check(value != null && !value.isBlank(), "Missing " + key); return value; }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
