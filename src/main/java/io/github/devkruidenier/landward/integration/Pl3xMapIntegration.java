package io.github.devkruidenier.landward.integration;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.ClaimedChunk;
import io.github.devkruidenier.landward.model.Land;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Pl3xMap exposes JSON marker layers; this bridge writes one managed layer per world. */
public final class Pl3xMapIntegration {
    private static final int PLAYER_GREEN = -13382400;
    private static final int ADMIN_PURPLE = -5745161;
    private final LandWardPlugin plugin;
    private final LandManager lands;
    private boolean failureLogged;

    public Pl3xMapIntegration(LandWardPlugin plugin, LandManager lands) { this.plugin = plugin; this.lands = lands; }

    public void sync() {
        if (!plugin.getConfig().getBoolean("integrations.pl3xmap.enabled", true)) return;
        Plugin pl3x = plugin.getServer().getPluginManager().getPlugin("Pl3xMap");
        if (pl3x == null || !pl3x.isEnabled()) return;
        for (World world : Bukkit.getWorlds()) {
            if (!plugin.isLandWorld(world)) continue;
            try {
                Path output = pl3x.getDataFolder().toPath().resolve("markers").resolve(world.getName()).resolve("landward_claims.json");
                Files.createDirectories(output.getParent());
                Files.writeString(output, layer(world), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                if (!failureLogged) { plugin.getLogger().warning("Pl3xMap was detected but LandWard markers could not be written: " + exception.getMessage()); failureLogged = true; }
            }
        }
    }

    private String layer(World world) {
        StringBuilder json = new StringBuilder("{\n  \"key\": \"landward_claims\",\n  \"label\": \"LandWard Claims\",\n  \"showControls\": true,\n  \"defaultHidden\": false,\n  \"markers\": [");
        boolean first = true;
        for (ClaimedChunk claim : lands.claims()) {
            if (!claim.position().worldId().equals(world.getUID())) continue;
            Land land = lands.land(claim.landId()).orElse(null);
            if (land == null) continue;
            if (!first) json.append(','); first = false;
            int x = claim.position().minBlockX(), z = claim.position().minBlockZ();
            boolean administrative = lands.isAdministrative(land);
            json.append("\n    {\"type\":\"rect\",\"data\":{\"key\":\"claim_").append(claim.position().x()).append('_').append(claim.position().z())
                    .append("\",\"point1\":{\"x\":").append(x).append(",\"z\":").append(z).append("},\"point2\":{\"x\":").append(claim.position().maxBlockXExclusive()).append(",\"z\":").append(claim.position().maxBlockZExclusive())
                    .append("}},\"options\":{\"tooltip\":{\"content\":\"").append(escape((administrative ? "LandWard admin claim: " : "LandWard: ") + land.name())).append("\"},\"fill\":{\"color\":").append(administrative ? ADMIN_PURPLE : PLAYER_GREEN).append(",\"opacity\":0.20}}}");
        }
        return json.append("\n  ]\n}\n").toString();
    }

    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
