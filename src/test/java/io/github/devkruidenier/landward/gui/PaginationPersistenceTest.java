package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.storage.sqlite.SqliteLandStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PaginationPersistenceTest {
    @TempDir Path directory;

    @Test void persists150DistinctOwnersAndClaimsAndDisplaysEachExactlyOnce() throws Exception {
        Path file = directory.resolve("landward.db");
        try (var storage = new SqliteLandStorage(file)) {
            storage.initialize();
            var manager = new LandManager(storage);
            manager.load();
            PaginationFixture.seed(manager, 150);
            PaginationFixture.seed(manager, 150); // Explicit repeat must be idempotent.
            manager.load(); // Normal service reload.
            verifyPages(manager);
        }
        // Fresh storage and service instances, as used following a process restart.
        try (var storage = new SqliteLandStorage(file)) {
            storage.initialize();
            var manager = new LandManager(storage);
            manager.load();
            verifyPages(manager);
            UUID unrelated = UUID.randomUUID();
            manager.create(unrelated, "Unrelated", new ChunkPosition(UUID.randomUUID(), 0, 0));
            PaginationFixture.clean(manager);
            assertEquals(1, manager.playerLands().size());
            assertTrue(manager.ownedBy(unrelated).isPresent());
            manager.load();
            assertEquals(1, manager.totalClaimCount());
        }
    }
    private void verifyPages(LandManager manager) {
        var lands = manager.playerLands();
        assertEquals(150, lands.size());
        assertEquals(150, lands.stream().map(land -> land.ownerId()).distinct().count());
        assertEquals(150, manager.claims().stream().map(claim -> claim.position()).distinct().count());
        assertEquals(4, GuiGrid.pages(lands.size()));
        int[] counts = {45, 45, 45, 15};
        int[] sizes = {54, 54, 54, 27};
        Set<UUID> displayed = new HashSet<>();
        for (int page = 0; page < 4; page++) {
            assertEquals(counts[page], GuiGrid.count(page, lands.size()));
            assertEquals(sizes[page], GuiGrid.size(counts[page]));
            for (int offset = 0; offset < counts[page]; offset++) {
                var land = lands.get(GuiGrid.start(page, lands.size()) + offset);
                assertTrue(displayed.add(land.id()), "Duplicate GUI entry");
                assertTrue(GuiGrid.slot(offset, counts[page]) < sizes[page] - 9);
                int number = Integer.parseInt(land.name().substring("TestLand".length()));
                var identity = PaginationFixture.identity(number);
                assertEquals(identity.id(), land.ownerId());
                assertEquals("player" + number, identity.name());
                PaginationFixture.verify(manager, identity);
            }
        }
        assertEquals(150, displayed.size());
        for (int page = 3; page >= 0; page--)
            assertEquals(counts[page], GuiGrid.count(page, lands.size()));
    }
    @Test void handlesAllBoundariesAndClampsAnEmptiedFinalPage() throws Exception {
        int[] totals = {0,1,9,10,18,19,27,28,36,37,45,46,90,91,135,136,150};
        for (int total : totals) {
            int seen = 0;
            for (int page = 0; page < GuiGrid.pages(total); page++) {
                int count = GuiGrid.count(page, total);
                assertEquals(seen, GuiGrid.start(page, total));
                assertTrue(count <= 45);
                assertTrue(GuiGrid.size(count) >= count + 9);
                seen += count;
            }
            assertEquals(total, seen);
        }
        try (var storage = new SqliteLandStorage(directory.resolve("boundary.db"))) {
            storage.initialize();
            var manager = new LandManager(storage);
            manager.load();
            PaginationFixture.seed(manager, 136);
            var last = manager.playerLands().getLast();
            assertEquals(1, GuiGrid.count(3, 136));
            manager.deleteOwnedLand(last.ownerId());
            assertEquals(2, GuiGrid.page(3, manager.playerLands().size()));
            assertEquals(45, GuiGrid.count(3, manager.playerLands().size()));
            assertEquals(54, GuiGrid.size(GuiGrid.count(3, manager.playerLands().size())));
        }
    }
}
