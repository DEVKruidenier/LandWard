package io.github.devkruidenier.landward.startup;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.console.LandWardConsole.Tone;

import java.util.Locale;
import java.util.Map;

public final class StartupReporter {

    private final LandWardPlugin plugin;

    public StartupReporter(LandWardPlugin plugin) {
        this.plugin = plugin;
    }

    public void log(int loadedClaims) {
        String version = plugin.getPluginMeta().getVersion();
        String storage = plugin.storage().type().toUpperCase(Locale.ROOT);
        String consoleLocale = plugin.getConfig().getString("language.console", "en_US");
        boolean metricsEnabled = plugin.metricsService().isInitialized();
        boolean updateCheckEnabled = plugin.getConfig().getBoolean("updates.enabled", true);

        plugin.console().bannerTop();
        plugin.console().bannerHeader(version);
        plugin.console().bannerDivider();
        row("◆", "startup.storage", "type", storage, Tone.INFO);
        row("◆", "startup.claims", "count", String.valueOf(loadedClaims), Tone.INFO);
        row("◆", "startup.language", "locale", consoleLocale, Tone.BRAND);
        row("●", "startup.metrics", "status", status(metricsEnabled), statusTone(metricsEnabled));
        row("●", "startup.update-check", "status", status(updateCheckEnabled), statusTone(updateCheckEnabled));
        plugin.console().bannerBottom();
    }

    private String status(boolean enabled) {
        return plugin.localeService().console(enabled ? "startup.status-enabled" : "startup.status-disabled");
    }

    private Tone statusTone(boolean enabled) {
        return enabled ? Tone.SUCCESS : Tone.WARNING;
    }

    private void row(String icon, String key, String placeholder, String value, Tone tone) {
        String template = plugin.localeService().console(key);
        String label = template.replace("{" + placeholder + "}", "").strip();
        if (label.endsWith(":")) {
            label = label.substring(0, label.length() - 1).strip();
        }
        plugin.console().bannerRow(icon, label, value, tone);
    }
}
