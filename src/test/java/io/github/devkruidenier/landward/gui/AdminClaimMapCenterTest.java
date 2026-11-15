package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.model.ClaimedChunk;
import io.github.devkruidenier.landward.model.ChunkPosition;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AdminClaimMapCenterTest {
    private final UUID localWorld = UUID.randomUUID();
    private final UUID remoteWorld = UUID.randomUUID();
    private final UUID selected = UUID.randomUUID();
    private final ChunkPosition administrator = new ChunkPosition(localWorld, 100, 200);

    @Test void selectedLandUsesItsRemoteWorldAndCoordinatesWithoutChangingTheViewerPosition() {
        var remote = new ChunkPosition(remoteWorld, -32, -9);
        var claims = List.of(new ClaimedChunk(administrator, UUID.randomUUID()), new ClaimedChunk(remote, selected));
        assertEquals(remote, AdminClaimMapCenter.select(administrator, selected, claims));
        assertNotEquals(administrator, remote); // Remote center must not get a current-player compass.
        assertEquals(administrator, AdminClaimMapCenter.select(administrator, null, claims));
    }

    @Test void selectingAnUnclaimedLandFallsBackToViewerWithoutUsingAnotherPlayersLand() {
        var claims = List.of(new ClaimedChunk(new ChunkPosition(remoteWorld, -32, -9), UUID.randomUUID()));
        assertEquals(administrator, AdminClaimMapCenter.select(administrator, selected, claims));
        assertEquals(administrator, AdminClaimMapCenter.select(administrator, selected, List.of()));
    }

    @Test void unorderedStorageResultsProduceTheSameSelectedLandCenter() {
        var first = new ClaimedChunk(new ChunkPosition(remoteWorld, -32, -9), selected);
        var second = new ClaimedChunk(new ChunkPosition(remoteWorld, -31, -9), selected);
        assertEquals(first.position(), AdminClaimMapCenter.select(administrator, selected, List.of(first, second)));
        assertEquals(first.position(), AdminClaimMapCenter.select(administrator, selected, List.of(second, first)));
    }

    @Test void fullDetailsMenuKeepsTeleportAndMapAsSeparateAdjacentActions() {
        var actions = List.of(10, 12, 14, 16, 18, 20);
        var positions = GuiPositions.center(GuiPositions.overrides("admin:PLAYER_CLAIM_DETAILS"),
                java.util.Set.copyOf(actions), 27);
        for (int i = 0; i < actions.size(); i++) assertEquals(10 + i, positions.get(actions.get(i)));
        assertNotEquals(positions.get(10), positions.get(18));
    }
}
