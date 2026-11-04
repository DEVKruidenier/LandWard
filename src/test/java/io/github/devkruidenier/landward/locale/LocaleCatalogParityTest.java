package io.github.devkruidenier.landward.locale;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ensures every bundled language has every player-visible LandWard message. */
final class LocaleCatalogParityTest {

    private static final List<String> LOCALES = List.of(
            "en_US", "nl_NL", "de_DE", "fr_FR", "es_ES", "pt_BR", "it_IT",
            "pl_PL", "tr_TR", "ru_RU", "zh_CN", "ja_JP", "ko_KR"
    );
    private static final Pattern KEY = Pattern.compile("^(\\s*)([A-Za-z0-9_-]+):(?:\\s+.+)?$");

    @Test
    void everyBundledCatalogMatchesTheEnglishMessageKeys() {
        Set<String> english = keys("en_US");
        for (String locale : LOCALES) {
            Set<String> translated = keys(locale);
            assertTrue(translated.containsAll(english),
                    "Language catalog is missing English message keys: " + locale);
        }
    }

    private Set<String> keys(String locale) {
        InputStream resource = getClass().getClassLoader().getResourceAsStream("lang/" + locale + ".yml");
        if (resource == null) throw new IllegalStateException("Missing bundled language: " + locale);
        Map<Integer, String> sections = new HashMap<>();
        List<String> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
                Matcher matcher = KEY.matcher(line);
                if (!matcher.matches()) continue;
                int depth = matcher.group(1).length() / 2;
                sections.put(depth, matcher.group(2));
                sections.keySet().removeIf(key -> key > depth);
                if (line.matches("^\\s*[A-Za-z0-9_-]+:\\s+.+$")) {
                    StringBuilder key = new StringBuilder();
                    for (int index = 0; index <= depth; index++) {
                        if (key.length() > 0) key.append('.');
                        key.append(sections.get(index));
                    }
                    result.add(key.toString());
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read language catalog " + locale, exception);
        }
        return Set.copyOf(result);
    }
}
