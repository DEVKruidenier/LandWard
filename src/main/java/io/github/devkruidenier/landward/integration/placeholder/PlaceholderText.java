package io.github.devkruidenier.landward.integration.placeholder;
import java.util.function.Function;
/** Configured values are language keys; empty values suppress fallback text. */
final class PlaceholderText {
    private PlaceholderText() {}
    static String resolve(String key, Function<String, String> translations) {
        return key == null || key.isBlank() ? "" : translations.apply(key.strip());
    }
}
