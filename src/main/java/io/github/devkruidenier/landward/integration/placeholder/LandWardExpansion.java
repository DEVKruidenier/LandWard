package io.github.devkruidenier.landward.integration.placeholder;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.model.Land;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Internal PlaceholderAPI expansion: %landward_<placeholder>%. */
public final class LandWardExpansion extends PlaceholderExpansion {
    private final LandWardPlugin plugin;
    private final LandManager lands;

    public LandWardExpansion(LandWardPlugin plugin, LandManager lands) { this.plugin = plugin; this.lands = lands; }
    @Override public @NotNull String getIdentifier() { return "landward"; }
    @Override public @NotNull String getAuthor() { return "DEVKruidenier"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return null;
        return switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "land_name" -> lands.ownedBy(player.getUniqueId()).map(Land::name)
                    .orElseGet(() -> configured(player.getPlayer(), "no-land-name"));
            case "is_owner" -> String.valueOf(lands.ownedBy(player.getUniqueId()).isPresent());
            case "owned_chunks" -> String.valueOf(lands.ownedBy(player.getUniqueId()).map(land -> lands.claimCount(land.id())).orElse(0));
            case "member_lands" -> String.valueOf(lands.memberLandCount(player.getUniqueId()));
            case "current_claim", "current_owner", "current_chunk_claimed" -> current(player.getPlayer(), params);
            default -> null;
        };
    }

    private String current(Player player, String parameter) {
        if (player == null) return parameter.equalsIgnoreCase("current_claim") ? "" : "none";
        ChunkPosition position = new ChunkPosition(player.getWorld().getUID(), player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4);
        Land land = lands.landAt(position).orElse(null);
        if (parameter.equalsIgnoreCase("current_claim")) return land == null
                ? configured(player, "unclaimed-land-name") : land.name();
        if (parameter.equalsIgnoreCase("current_chunk_claimed")) return String.valueOf(land != null);
        if (land == null) return configured(player, "unclaimed-owner-name");
        if (lands.isAdministrative(land)) return configured(player, "server-owner-name");
        return parameter.equalsIgnoreCase("current_owner")
                ? plugin.getServer().getOfflinePlayer(land.ownerId()).getName() == null ? land.ownerId().toString() : plugin.getServer().getOfflinePlayer(land.ownerId()).getName()
                : land.name();
    }

    private String configured(Player player, String setting) {
        return PlaceholderText.resolve(plugin.getConfig().getString("placeholders." + setting, ""),
                key -> plugin.localeService().message(plugin.localeService().localeFor(player), key));
    }
}
