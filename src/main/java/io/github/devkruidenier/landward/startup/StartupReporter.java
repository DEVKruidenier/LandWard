package io.github.devkruidenier.landward.startup;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.console.LandWardConsole.Tone;

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

        plugin.console().info(DIVIDER, Tone.MUTED);
        info("startup.header", Map.of("version", version), Tone.BRAND);
        info("startup.storage", Map.of("type", storage), Tone.INFO);
        info("startup.claims", Map.of("count", loadedClaims), Tone.INFO);
        info("startup.language", Map.of("locale", consoleLocale), Tone.INFO);
        info("startup.metrics", Map.of("status", status(metricsEnabled)), statusTone(metricsEnabled));
        info("startup.update-check", Map.of("status", status(updateCheckEnabled)), statusTone(updateCheckEnabled));
        plugin.console().info(DIVIDER, Tone.MUTED);
    }

    private String status(boolean enabled) {
        return plugin.localeService().console(enabled ? "startup.status-enabled" : "startup.status-disabled");
    }

    private Tone statusTone(boolean enabled) {
        return enabled ? Tone.SUCCESS : Tone.WARNING;
    }

    private void info(String key, Map<String, ?> placeholders, Tone tone) {
        plugin.console().info(plugin.localeService().console(key), placeholders, tone);
    }
}
