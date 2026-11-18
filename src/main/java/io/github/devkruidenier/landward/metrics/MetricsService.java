package io.github.devkruidenier.landward.metrics;

import io.github.devkruidenier.landward.LandWardPlugin;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.DrilldownPie;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.locale.LocaleService;
import io.github.devkruidenier.landward.storage.mysql.MySqlLandStorage;
import io.github.devkruidenier.landward.storage.sqlite.SqliteLandStorage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

public final class MetricsService {

    public static final int BSTATS_PLUGIN_ID = 35128;

    private final LandWardPlugin plugin;
    private Metrics metrics;
    private final LandManager lands;
    private BukkitTask refreshTask;
    private volatile UsageSnapshot snapshot;

    public MetricsService(LandWardPlugin plugin, LandManager lands) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lands = Objects.requireNonNull(lands, "lands");
    }

    public void initialize() {
        shutdown();
        if (!plugin.getConfig().getBoolean("metrics.enabled", true)) {
            return;
        }

        this.metrics = new Metrics(plugin, BSTATS_PLUGIN_ID);
        // bStats owns this opt-out file. Read it only; never override its values.
        var global = new java.io.File(plugin.getDataFolder().getParentFile(), "bStats/config.yml");
        if (!YamlConfiguration.loadConfiguration(global).getBoolean("enabled", true)) {
            shutdown();
            return;
        }
        refresh();
        for (String id : snapshot.charts().keySet()) {
            metrics.addCustomChart(new SimplePie(id, () -> {
                UsageSnapshot current = snapshot;
                return current == null ? null : current.charts().get(id);
            }));
        }
        metrics.addCustomChart(new DrilldownPie("integration_status", () -> {
            UsageSnapshot current = snapshot;
            return current == null ? null : current.integrationChart();
        }));
        // Bukkit/config access stays on the server thread; submitters read immutable categories only.
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, 1200L, 1200L);
    }

    private void refresh() {
        long[] counts = lands.usageCounts();
        var values = new java.util.HashMap<String, String>();
        values.put("storage_backend", plugin.storage() instanceof SqliteLandStorage ? "SQLite"
                : plugin.storage() instanceof MySqlLandStorage ? "MySQL/MariaDB" : "Unknown");
        values.put("configured_language", UsageSnapshot.language(plugin.getConfig().getString("language.default"), LocaleService.supportedLocales()));
        values.put("player_lands", UsageSnapshot.bucket(counts[0]));
        values.put("claimed_chunks", UsageSnapshot.bucket(counts[1]));
        values.put("safe_zones", UsageSnapshot.bucket(counts[2]));
        values.put("land_warps", UsageSnapshot.bucket(counts[3]));
        values.put("payment_mode", UsageSnapshot.paymentMode(plugin.getConfig().getString("payments.mode")));
        var integrations = new java.util.HashMap<String, Boolean>();
        for (String name : UsageSnapshot.INTEGRATIONS) {
            boolean configured = name.equals("WorldGuard") || name.equals("Vault")
                    || plugin.getConfig().getBoolean("integrations." + name.toLowerCase(java.util.Locale.ROOT) + ".enabled", true);
            integrations.put(name, configured && plugin.getServer().getPluginManager().isPluginEnabled(name));
        }
        snapshot = new UsageSnapshot(values, integrations);
    }

    public void shutdown() {
        snapshot = null;
        if (refreshTask != null) { refreshTask.cancel(); refreshTask = null; }
        if (metrics != null) { metrics.shutdown(); metrics = null; }
    }

    public boolean isInitialized() {
        return metrics != null;
    }
}
