package io.github.devkruidenier.landward.metrics;

import io.github.devkruidenier.landward.LandWardPlugin;
import org.bstats.bukkit.Metrics;

import java.util.Objects;

public final class MetricsService {

    public static final int BSTATS_PLUGIN_ID = 35128;

    private final LandWardPlugin plugin;
    private Metrics metrics;

    public MetricsService(LandWardPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void initialize() {
        if (!plugin.getConfig().getBoolean("metrics.enabled", true)) {
            return;
        }

        this.metrics = new Metrics(plugin, BSTATS_PLUGIN_ID);
    }

    public boolean isInitialized() {
        return metrics != null;
    }
}
