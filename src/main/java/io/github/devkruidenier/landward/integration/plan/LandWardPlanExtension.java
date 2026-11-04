package io.github.devkruidenier.landward.integration.plan;

import com.djrapitops.plan.extension.CallEvents;
import com.djrapitops.plan.extension.DataExtension;
import com.djrapitops.plan.extension.annotation.BooleanProvider;
import com.djrapitops.plan.extension.annotation.NumberProvider;
import com.djrapitops.plan.extension.annotation.PluginInfo;
import com.djrapitops.plan.extension.icon.Color;
import com.djrapitops.plan.extension.icon.Family;
import io.github.devkruidenier.landward.claim.LandManager;

import java.util.UUID;

/**
 * Provides read-only LandWard statistics to Plan's web panel.
 *
 * <p>This class deliberately contains all direct Plan API references. It is only
 * loaded after the optional Plan plugin was detected by {@link PlanIntegration}.</p>
 */
@PluginInfo(name = "LandWard", iconName = "map", iconFamily = Family.SOLID, color = Color.GREEN)
public final class LandWardPlanExtension implements DataExtension {

    private final LandManager landManager;

    public LandWardPlanExtension(LandManager landManager) {
        this.landManager = landManager;
    }

    @Override
    public CallEvents[] callExtensionMethodsOn() {
        return new CallEvents[]{
                CallEvents.PLAYER_JOIN,
                CallEvents.PLAYER_LEAVE,
                CallEvents.PLAYER_PERIODICAL,
                CallEvents.SERVER_EXTENSION_REGISTER,
                CallEvents.SERVER_PERIODICAL
        };
    }

    @BooleanProvider(
            text = "Owns a land",
            description = "Whether this player owns a LandWard land.",
            priority = 30,
            iconName = "map",
            iconFamily = Family.SOLID,
            iconColor = Color.GREEN,
            showInPlayerTable = true
    )
    public boolean ownsLand(UUID playerId) {
        return landManager.ownedBy(playerId).isPresent();
    }

    @NumberProvider(
            text = "Owned chunks",
            description = "Chunks claimed in the player's own LandWard land.",
            priority = 20,
            iconName = "square",
            iconFamily = Family.SOLID,
            iconColor = Color.GREEN,
            showInPlayerTable = true
    )
    public long ownedChunks(UUID playerId) {
        return landManager.ownedBy(playerId)
                .map(land -> (long) landManager.claimCount(land.id()))
                .orElse(0L);
    }

    @NumberProvider(
            text = "Member lands",
            description = "Other LandWard lands where this player is a member.",
            priority = 10,
            iconName = "users",
            iconFamily = Family.SOLID,
            iconColor = Color.BLUE,
            showInPlayerTable = false
    )
    public long memberLands(UUID playerId) {
        return landManager.memberLandCount(playerId);
    }

    @NumberProvider(
            text = "Lands",
            description = "Total LandWard lands on this server.",
            priority = 20,
            iconName = "map",
            iconFamily = Family.SOLID,
            iconColor = Color.GREEN,
            showInPlayerTable = false
    )
    public long lands() {
        return landManager.totalLandCount();
    }

    @NumberProvider(
            text = "Claimed chunks",
            description = "Total chunks protected by LandWard on this server.",
            priority = 10,
            iconName = "square",
            iconFamily = Family.SOLID,
            iconColor = Color.GREEN,
            showInPlayerTable = false
    )
    public long claimedChunks() {
        return landManager.totalClaimCount();
    }
}
