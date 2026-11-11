package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.storage.sqlite.SqliteLandStorage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Explicit developer fixture. This class is never packaged in either production artifact. */
public final class PaginationFixture {
    static final UUID WORLD = UUID.nameUUIDFromBytes("LandWard pagination fixture world".getBytes(StandardCharsets.UTF_8));
    record Identity(int number, String name, UUID id, String landName, ChunkPosition chunk) {}
    static Identity identity(int number) {
        String name = "player" + number;
        UUID id = UUID.nameUUIDFromBytes(("LandWardTestFixture:" + name).getBytes(StandardCharsets.UTF_8));
        return new Identity(number, name, id, "TestLand" + number,
                new ChunkPosition(WORLD, 10000 + ((number - 1) % 15) * 4, 10000 + ((number - 1) / 15) * 4));
    }
    static void seed(LandManager manager, int count) throws Exception {
        for (int number = 1; number <= count; number++) {
            Identity identity = identity(number);
            if (manager.ownedBy(identity.id()).isEmpty()) {
                var result = manager.create(identity.id(), identity.landName(), identity.chunk());
                if (result.status() != LandManager.CreateStatus.CREATED)
                    throw new IllegalStateException("Fixture collision: " + identity.name() + " " + result.status());
            }
            verify(manager, identity);
        }
    }
    static void verify(LandManager manager, Identity identity) {
        var land = manager.ownedBy(identity.id()).orElseThrow();
        if (!land.name().equals(identity.landName()) || manager.claimCount(land.id()) != 1
                || !manager.landAt(identity.chunk()).map(value -> value.id().equals(land.id())).orElse(false)
                || !manager.members(land.id()).isEmpty())
            throw new IllegalStateException("Fixture record differs; refusing to alter it: " + identity.name());
    }
    static void clean(LandManager manager) throws Exception {
        for (int number = 1; number <= 150; number++) {
            Identity identity = identity(number);
            if (manager.ownedBy(identity.id()).isPresent()) {
                verify(manager, identity);
                manager.deleteOwnedLand(identity.id());
            }
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !Set.of("seed", "verify", "clean").contains(args[0]))
            throw new IllegalArgumentException("Expected seed|verify|clean and an isolated fixture directory");
        Path directory = Path.of(args[1]).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        Path marker = directory.resolve("LANDWARD-PAGINATION-TEST-ONLY");
        Path database = directory.resolve("landward.db");
        if (Files.exists(database) && !Files.exists(marker))
            throw new IllegalStateException("Existing database has no fixture marker; refusing access");
        if (!Files.exists(marker)) Files.writeString(marker, "Isolated pagination fixture. Never use as a production database.\n");
        try (var storage = new SqliteLandStorage(database)) {
            storage.initialize();
            LandManager manager = new LandManager(storage);
            manager.load();
            if (args[0].equals("seed")) seed(manager, 150);
            else if (args[0].equals("clean")) clean(manager);
            if (!args[0].equals("clean")) {
                for (int number = 1; number <= 150; number++) verify(manager, identity(number));
                List<String> identities = new ArrayList<>(List.of("name\tuuid\tland\tworld\tchunkX\tchunkZ"));
                for (int number = 1; number <= 150; number++) {
                    Identity identity = identity(number);
                    identities.add(identity.name() + "\t" + identity.id() + "\t" + identity.landName()
                            + "\t" + WORLD + "\t" + identity.chunk().x() + "\t" + identity.chunk().z());
                }
                Files.write(directory.resolve("identities.tsv"), identities, StandardCharsets.UTF_8);
            }
            System.out.println("Fixture operation " + args[0] + ": " + manager.playerLands().size()
                    + " lands, " + manager.totalClaimCount() + " chunks in " + directory);
        }
    }
}
