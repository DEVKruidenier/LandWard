package io.github.devkruidenier.landward.locale;

import io.github.devkruidenier.landward.LandWardPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LocaleService {

    public static final String FALLBACK_LOCALE = "en_US";

    private static final List<String> BUNDLED_LOCALES = List.of(
            "en_US", "nl_NL", "de_DE", "fr_FR", "es_ES", "pt_BR", "it_IT",
            "pl_PL", "tr_TR", "ru_RU", "zh_CN", "ja_JP", "ko_KR"
    );

    private final LandWardPlugin plugin;
    private final Map<String, YamlConfiguration> translations = new ConcurrentHashMap<>();

    public LocaleService(LandWardPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        File directory = new File(plugin.getDataFolder(), "lang");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create language directory: " + directory);
        }

        for (String locale : BUNDLED_LOCALES) {
            String resource = "lang/" + locale + ".yml";
            File target = new File(plugin.getDataFolder(), resource);
            if (!target.exists()) {
                plugin.saveResource(resource, false);
            }
            YamlConfiguration configured = YamlConfiguration.loadConfiguration(target);
            try (InputStream input = plugin.getResource(resource)) {
                if (input != null) {
                    YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                            new InputStreamReader(input, StandardCharsets.UTF_8)
                    );
                    configured.setDefaults(bundled);
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Could not load bundled language: " + resource, exception);
            }
            translations.put(locale, configured);
        }
    }

    public String console(String key) {
        String configured = normalize(plugin.getConfig().getString("language.console", FALLBACK_LOCALE));
        return message(configured, key);
    }

    public String console(String key, Map<String, ?> placeholders) {
        String value = console(key);
        for (Map.Entry<String, ?> placeholder : placeholders.entrySet()) {
            value = value.replace("{" + placeholder.getKey() + "}", String.valueOf(placeholder.getValue()));
        }
        return value;
    }

    public String message(String locale, String key) {
        YamlConfiguration selected = translations.getOrDefault(normalize(locale), translations.get(FALLBACK_LOCALE));
        String value = selected == null ? null : selected.getString(key);
        if (value != null) {
            return value;
        }

        YamlConfiguration fallback = translations.get(FALLBACK_LOCALE);
        return fallback == null ? key : fallback.getString(key, key);
    }

    public String message(String locale, String key, Map<String, ?> placeholders) {
        String value = message(locale, key);
        for (Map.Entry<String, ?> placeholder : placeholders.entrySet()) {
            value = value.replace("{" + placeholder.getKey() + "}", String.valueOf(placeholder.getValue()));
        }
        return value;
    }

    public String localeFor(CommandSender sender) {
        if (sender instanceof Player player
                && plugin.getConfig().getBoolean("language.use-client-locale", true)) {
            return normalize(player.locale().toString());
        }
        return normalize(plugin.getConfig().getString("language.default", FALLBACK_LOCALE));
    }

    private String normalize(String locale) {
        if (locale == null || locale.isBlank()) {
            return FALLBACK_LOCALE;
        }

        String[] parts = locale.replace('-', '_').split("_", 2);
        if (parts.length == 1) {
            return parts[0].toLowerCase(Locale.ROOT);
        }
        return parts[0].toLowerCase(Locale.ROOT) + "_" + parts[1].toUpperCase(Locale.ROOT);
    }
}
