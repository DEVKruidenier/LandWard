package io.github.devkruidenier.landward.integration;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.ClaimedChunk;
import io.github.devkruidenier.landward.model.Land;
import org.bukkit.Bukkit;
import org.bukkit.World;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.SimpleLayerProvider;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.SquaremapProvider;
import xyz.jpenilla.squaremap.api.WorldIdentifier;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;

import java.awt.Color;
import java.util.List;

/** Optional Squaremap layer. All Squaremap references are isolated in this class. */
public final class SquaremapIntegration {
    private static final Key LAYER_KEY = Key.of("landward_claims");
    private final LandWardPlugin plugin;
    private final LandManager lands;

    public SquaremapIntegration(LandWardPlugin plugin, LandManager lands) { this.plugin = plugin; this.lands = lands; }

    public void sync() {
        if (!plugin.getConfig().getBoolean("integrations.squaremap.enabled", true)
                || plugin.getServer().getPluginManager().getPlugin("squaremap") == null) return;
        Squaremap map = SquaremapProvider.get();
        for (World world : Bukkit.getWorlds()) {
            map.getWorldIfEnabled(WorldIdentifier.create("minecraft", world.getName())).ifPresent(mapWorld -> {
                var registry = mapWorld.layerRegistry();
                if (registry.hasEntry(LAYER_KEY)) registry.unregister(LAYER_KEY);
                SimpleLayerProvider layer = SimpleLayerProvider.builder("LandWard Claims").showControls(true).defaultHidden(false).build();
                for (ClaimedChunk claim : lands.claims()) {
                    if (!claim.position().worldId().equals(world.getUID())) continue;
                    Land land = lands.land(claim.landId()).orElse(null);
                    if (land == null) continue;
                    int x = claim.position().x() << 4, z = claim.position().z() << 4;
                    Marker marker = Marker.polygon(List.of(Point.of(x, z), Point.of(x + 16, z), Point.of(x + 16, z + 16), Point.of(x, z + 16)))
                            .markerOptions(MarkerOptions.builder().strokeColor(Color.GREEN).fillColor(Color.GREEN).fillOpacity(0.20D).hoverTooltip("LandWard: " + land.name()));
                    layer.addMarker(Key.of("claim_" + claim.position().x() + "_" + claim.position().z()), marker);
                }
                registry.register(LAYER_KEY, layer);
            });
        }
    }
}
