package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.storage.sqlite.SqliteLandStorage;
import org.yaml.snakeyaml.Yaml;
import java.io.DataInputStream;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Explicit offline developer operation for an existing local TestServer directory only. */
public final class ExistingServerFixture {
    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !Set.of("seed", "verify", "clean").contains(args[0]))
            throw new IllegalArgumentException("Expected seed|verify|clean and local TestServer directory");
        Path server = Path.of(args[1]).toRealPath();
        if (!server.getFileName().toString().contains("TestServer")
                || !Files.isRegularFile(server.resolve("Start LandWard Test Server.cmd")))
            throw new IllegalArgumentException("Not a recognized local LandWard test server");
        var properties = new java.util.Properties();
        try (var input = Files.newInputStream(server.resolve("server.properties"))) { properties.load(input); }
        Path world = server.resolve(properties.getProperty("level-name", "world")).normalize();
        if (!Files.isRegularFile(world.resolve("uid.dat"))) {
            try (var directories = Files.list(server)) {
                var candidates = directories.filter(path -> Files.isRegularFile(path.resolve("uid.dat")))
                        .filter(path -> !path.getFileName().toString().endsWith("_nether"))
                        .filter(path -> !path.getFileName().toString().endsWith("_the_end")).toList();
                if (candidates.size() != 1) throw new IllegalStateException("Cannot identify primary test world");
                world = candidates.getFirst();
            }
        }
        if (!world.startsWith(server)) throw new IllegalArgumentException("World is outside the test server");
        try (var lockFile = FileChannel.open(world.resolve("session.lock"), StandardOpenOption.WRITE);
             var lock = lockFile.tryLock()) {
            if (lock == null) throw new IllegalStateException("Stop the test server first");
            UUID worldId;
            try (var input = new DataInputStream(Files.newInputStream(world.resolve("uid.dat")))) {
                worldId = new UUID(input.readLong(), input.readLong());
            }
            Path data = server.resolve("plugins/LandWard");
            Map<?, ?> config = new Yaml().load(Files.readString(data.resolve("config.yml")));
            if (!(config.get("storage") instanceof Map<?, ?> storageConfig)
                    || !"SQLITE".equalsIgnoreCase(String.valueOf(storageConfig.get("type"))))
                throw new IllegalStateException("Fixture requires the test server to use SQLite");
            Path cache = server.resolve("usercache.json");
            List<Map<String, String>> profiles = new Yaml().load(Files.readString(cache));
            if (profiles == null) profiles = new ArrayList<>();
            Set<String> fixtureIds = new HashSet<>();
            for (int i = 1; i <= 150; i++) {
                var identity = PaginationFixture.identity(i);
                fixtureIds.add(identity.id().toString());
                for (var profile : profiles) {
                    if (identity.name().equalsIgnoreCase(profile.get("name"))
                            && !identity.id().toString().equals(profile.get("uuid")))
                        throw new IllegalStateException("Test profile name already belongs to another UUID: " + identity.name());
                }
            }
            try (var storage = new SqliteLandStorage(data.resolve("landward.db"))) {
                storage.initialize();
                LandManager manager = new LandManager(storage);
                manager.load();
                int processed = 0;
                for (int i = 1; i <= 150; i++) {
                    var identity = PaginationFixture.identity(i);
                    ChunkPosition chunk = new ChunkPosition(worldId, identity.chunk().x(), identity.chunk().z());
                    var existing = manager.ownedBy(identity.id());
                    if (existing.isEmpty() && args[0].equals("seed")) {
                        var result = manager.create(identity.id(), identity.landName(), chunk);
                        if (result.status() != LandManager.CreateStatus.CREATED)
                            throw new IllegalStateException("Fixture collision: " + result.status());
                        existing = Optional.of(result.land());
                    }
                    if (existing.isPresent()) {
                        var land = existing.get();
                        if (!land.name().equals(identity.landName()) || manager.claimCount(land.id()) != 1
                                || !manager.landAt(chunk).map(value -> value.id().equals(land.id())).orElse(false)
                                || !manager.members(land.id()).isEmpty())
                            throw new IllegalStateException("Fixture data differs; refusing changes: " + identity.name());
                        if (args[0].equals("clean")) manager.deleteOwnedLand(identity.id());
                        processed++;
                    } else if (args[0].equals("verify")) throw new IllegalStateException("Missing fixture owner: " + identity.name());
                }
                if (!args[0].equals("verify")) {
                    profiles.removeIf(profile -> fixtureIds.contains(profile.get("uuid")));
                    if (args[0].equals("seed")) for (int i = 1; i <= 150; i++) {
                        var identity = PaginationFixture.identity(i);
                        profiles.add(Map.of("name", identity.name(), "uuid", identity.id().toString(), "expiresOn", "2099-01-01 00:00:00 +0000"));
                    }
                    List<String> json = profiles.stream().map(profile -> "{\"name\":" + quote(profile.get("name"))
                            + ",\"uuid\":" + quote(profile.get("uuid")) + ",\"expiresOn\":" + quote(profile.get("expiresOn")) + "}").toList();
                    Files.writeString(cache, "[" + String.join(",", json) + "]");
                }
                System.out.println("Test-server fixture " + args[0] + ": " + processed + " verified fixture owners; "
                        + manager.playerLands().size() + " total player lands. Unrelated lands preserved.");
            }
        }
    }
    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
