package io.github.devkruidenier.landward.integration.plan;

import com.djrapitops.plan.extension.CallEvents;
import com.djrapitops.plan.extension.DataExtension;
import com.djrapitops.plan.extension.ElementOrder;
import com.djrapitops.plan.extension.annotation.NumberProvider;
import com.djrapitops.plan.extension.annotation.PluginInfo;
import com.djrapitops.plan.extension.annotation.StringProvider;
import com.djrapitops.plan.extension.annotation.Tab;
import com.djrapitops.plan.extension.annotation.TabInfo;
import com.djrapitops.plan.extension.annotation.TableProvider;
import com.djrapitops.plan.extension.icon.Color;
import com.djrapitops.plan.extension.icon.Family;
import com.djrapitops.plan.extension.icon.Icon;
import com.djrapitops.plan.extension.table.Table;
import io.github.devkruidenier.landward.claim.LandManager;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Provides read-only LandWard statistics to Plan's web panel.
 *
 * <p>This class deliberately contains all direct Plan API references. It is only
 * loaded after the optional Plan plugin was detected by {@link PlanIntegration}.</p>
 */
@PluginInfo(name = "LandWard", iconName = "map", iconFamily = Family.SOLID, color = Color.GREEN)
@TabInfo(
        tab = "Overview",
        iconName = "map",
        iconFamily = Family.SOLID,
        elementOrder = {ElementOrder.VALUES, ElementOrder.TABLE}
)
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

    @Tab("Overview")
    @NumberProvider(
            text = "Player lands",
            description = "Normal player-owned lands on this server.",
            priority = 60,
            iconName = "map",
            iconFamily = Family.SOLID,
            iconColor = Color.GREEN,
            showInPlayerTable = false
    )
    public long playerLands() {
        return landManager.playerLandCount();
    }

    @Tab("Overview")
    @NumberProvider(
            text = "Claimed chunks",
            description = "Protected chunks belonging to player lands.",
            priority = 50,
            iconName = "square",
            iconFamily = Family.SOLID,
            iconColor = Color.GREEN,
            showInPlayerTable = false
    )
    public long playerClaimedChunks() {
        return landManager.playerClaimCount();
    }

    @Tab("Overview")
    @NumberProvider(
            text = "Safe Zones",
            description = "Server-owned administrative claims.",
            priority = 40,
            iconName = "shield",
            iconFamily = Family.SOLID,
            iconColor = Color.PURPLE,
            showInPlayerTable = false
    )
    public long safeZones() {
        return landManager.administrativeLandCount();
    }

    @Tab("Overview")
    @NumberProvider(
            text = "Safe Zone chunks",
            description = "Protected chunks belonging to Safe Zones.",
            priority = 30,
            iconName = "shield",
            iconFamily = Family.SOLID,
            iconColor = Color.PURPLE,
            showInPlayerTable = false
    )
    public long safeZoneChunks() {
        return landManager.administrativeClaimCount();
    }

    @Tab("Overview")
    @NumberProvider(
            text = "Unique land owners",
            description = "Players who own one normal player land.",
            priority = 20,
            iconName = "user",
            iconFamily = Family.SOLID,
            iconColor = Color.BLUE,
            showInPlayerTable = false
    )
    public long uniqueLandOwners() {
        return landManager.playerLandCount();
    }

    @Tab("Overview")
    @NumberProvider(
            text = "Land memberships",
            description = "Active player memberships, excluding owners and Safe Zones.",
            priority = 10,
            iconName = "users",
            iconFamily = Family.SOLID,
            iconColor = Color.BLUE,
            showInPlayerTable = false
    )
    public long landMemberships() {
        return landManager.playerMembershipCount();
    }

    @Tab("Overview")
    @StringProvider(
            text = "Owned land",
            description = "The player's land and its claimed-chunk count.",
            priority = 20,
            iconName = "map",
            iconFamily = Family.SOLID,
            iconColor = Color.GREEN,
            showInPlayerTable = false
    )
    public String ownedLand(UUID playerId) {
        return landManager.ownedBy(playerId)
                .map(land -> land.name() + " (" + landManager.claimCount(land.id()) + " chunks)")
                .orElse("None");
    }

    @Tab("Overview")
    @StringProvider(
            text = "Land memberships",
            description = "Other player lands where this player is a member.",
            priority = 10,
            iconName = "users",
            iconFamily = Family.SOLID,
            iconColor = Color.BLUE,
            showInPlayerTable = false
    )
    public String memberLandNames(UUID playerId) {
        String names = landManager.memberLands(playerId).stream().map(land -> land.name() + " (" + landManager.claimCount(land.id()) + " chunks)")
                .collect(Collectors.joining(", "));
        return names.isBlank() ? "None" : names;
    }

    /**
     * A three-column table intentionally makes this a wide Plan extension.
     * Plan then exposes LandWard as its own clickable item in the left sidebar.
     */
    @Tab("Overview")
    @TableProvider(tableColor = Color.GREEN)
    public Table landWardOverview() {
        return Table.builder()
                .columnOne("Type", new Icon(Family.SOLID, "map", Color.GREEN))
                .columnTwo("Metric", new Icon(Family.SOLID, "chart-bar", Color.BLUE))
                .columnThree("Value", new Icon(Family.SOLID, "hashtag", Color.GREEN))
                .addRow("Player lands", "Lands", playerLands())
                .addRow("Player lands", "Claimed chunks", playerClaimedChunks())
                .addRow("Safe Zones", "Safe Zones", safeZones())
                .addRow("Safe Zones", "Claimed chunks", safeZoneChunks())
                .addRow("Players", "Unique land owners", uniqueLandOwners())
                .addRow("Players", "Land memberships", landMemberships())
                .build();
    }
}
