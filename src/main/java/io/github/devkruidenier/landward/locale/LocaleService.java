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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class LocaleService {

    public static final String FALLBACK_LOCALE = "en_US";
    /** Plugin identity and official update metadata are intentionally not user-configurable. */
    private static final Set<String> PROTECTED_METADATA_KEYS = Set.of(
            "command.info-author", "command.info-author-hover", "command.info-releases-hover",
            "command.info-latest", "command.info-outdated", "command.info-checking"
    );

    private static final List<String> BUNDLED_LOCALES = LocaleCatalog.LOCALES;

    private final LandWardPlugin plugin;
    public static Set<String> supportedLocales() { return LocaleCatalog.supported(); }
    private volatile Map<String, YamlConfiguration> translations = Map.of();

    public LocaleService(LandWardPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void initialize() {
        Map<String, YamlConfiguration> loaded = new java.util.HashMap<>();
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
                    // Merge newly shipped language keys into an existing file without
                    // replacing any wording a server owner has customised.
                    configured.options().copyDefaults(true);
                    PROTECTED_METADATA_KEYS.forEach(key -> configured.set(key, null));
                    configured.save(target);
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Could not load bundled language: " + resource, exception);
            }
            loaded.put(locale, configured);
        }
        translations = Map.copyOf(loaded);
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
        if (sender instanceof Player remote && plugin.scheduler().folia() && !plugin.scheduler().owns(remote)) {
            var snapshot=plugin.playerSnapshots().get(remote.getUniqueId());
            if (snapshot!=null && plugin.getConfig().getBoolean("language.use-client-locale",true)) return LocaleCatalog.forClient(snapshot.locale(),plugin.getConfig().getString("language.default",FALLBACK_LOCALE));
            return normalize(plugin.getConfig().getString("language.default",FALLBACK_LOCALE));
        }
        if (sender instanceof Player player
                && plugin.getConfig().getBoolean("language.use-client-locale", true)) {
            // Spigot exposes the client locale as a legacy String, while Paper
            // also offers an Adventure Locale. The String API works on both.
            return LocaleCatalog.forClient(player.getLocale(), plugin.getConfig().getString("language.default", FALLBACK_LOCALE));
        }
        return LocaleCatalog.configured(plugin.getConfig().getString("language.default", FALLBACK_LOCALE));
    }

    private String normalize(String locale) {
        return LocaleCatalog.normalize(locale);
    }
}
