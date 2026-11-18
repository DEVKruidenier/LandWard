package io.github.devkruidenier.landward.locale;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Canonical filenames and explicit client aliases, independent of Bukkit. */
public final class LocaleCatalog {
    public static final String FALLBACK = "en_US";
    public static final List<String> LOCALES = List.of(
            "en_US", "nl_NL", "de_DE", "fr_FR", "es_ES", "pt_BR", "it_IT", "pl_PL", "tr_TR", "ru_RU", "zh_CN", "ja_JP", "ko_KR",
            "fi_FI", "da_DK", "sv_SE", "nb_NO", "is_IS", "fo_FO", "nn_NO", "id_ID", "vi_VN", "cs_CZ", "ro_RO", "uk_UA", "hu_HU",
            "th_TH", "fil_PH", "ms_MY", "hi_IN", "ar_SA", "zh_TW");
    private static final Set<String> SUPPORTED = Set.copyOf(LOCALES);
    private LocaleCatalog() {}
    public static Set<String> supported() { return SUPPORTED; }
    public static String normalize(String value) {
        if (value == null || value.isBlank()) return FALLBACK;
        String[] parts = value.strip().replace('-', '_').split("_", 2);
        String locale = parts[0].toLowerCase(Locale.ROOT) + (parts.length == 2 ? "_" + parts[1].toUpperCase(Locale.ROOT) : "");
        // Java Edition sends no_no; Bedrock/Geyser and administrator configs may use nb_NO.
        return locale.equals("no_NO") ? "nb_NO" : locale;
    }
    public static String configured(String value) {
        String normalized = normalize(value);
        return SUPPORTED.contains(normalized) ? normalized : FALLBACK;
    }
    public static String forClient(String client, String configuredDefault) {
        if (client == null || client.isBlank()) return configured(configuredDefault);
        String normalized = normalize(client);
        return SUPPORTED.contains(normalized) ? normalized : configured(configuredDefault);
    }
}
