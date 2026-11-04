package io.github.devkruidenier.landward.startup;

import io.github.devkruidenier.landward.LandWardPlugin;

import java.util.Locale;
import java.util.Map;

public final class StartupReporter {

    private static final String DIVIDER = "--------------------------------------------------";

    private final LandWardPlugin plugin;

    public StartupReporter(LandWardPlugin plugin) {
        this.plugin = plugin;
    }

    public void log(int loadedClaims) {
        String version = plugin.getPluginMeta().getVersion();
        String storage = plugin.getConfig().getString("storage.type", "sqlite").toUpperCase(Locale.ROOT);
        String consoleLocale = plugin.getConfig().getString("language.console", "en_US");
        boolean metricsEnabled = plugin.metricsService().isInitialized();
        boolean updateCheckEnabled = plugin.getConfig().getBoolean("updates.enabled", true);

        plugin.getLogger().info(DIVIDER);
        info("startup.header", Map.of("version", version));
        info("startup.storage", Map.of("type", storage));
        info("startup.claims", Map.of("count", loadedClaims));
        info("startup.language", Map.of("locale", consoleLocale));
        info("startup.metrics", Map.of("status", status(metricsEnabled)));
        info("startup.update-check", Map.of("status", status(updateCheckEnabled)));
        plugin.getLogger().info(DIVIDER);
    }

    private String status(boolean enabled) {
        return plugin.localeService().console(enabled ? "startup.status-enabled" : "startup.status-disabled");
    }

    private void info(String key, Map<String, ?> placeholders) {
        plugin.getLogger().info(plugin.localeService().console(key, placeholders));
    }
}
