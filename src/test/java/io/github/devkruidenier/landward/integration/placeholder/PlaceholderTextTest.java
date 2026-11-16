package io.github.devkruidenier.landward.integration.placeholder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PlaceholderTextTest {
    @Test void emptyValuesDoNotResolveTranslations() {
        for (String key : new String[]{null, "", " "})
            assertEquals("", PlaceholderText.resolve(key, ignored -> { throw new AssertionError(); }));
    }
    @Test void configuredKeyUsesTheSelectedLanguage() {
        assertEquals("Ongeclaimd", PlaceholderText.resolve("placeholders.unclaimed", key -> "Ongeclaimd"));
        assertEquals("Unclaimed", PlaceholderText.resolve("placeholders.unclaimed", key -> "Unclaimed"));
        assertEquals("Geen land", PlaceholderText.resolve(" placeholders.no-land ", key -> {
            assertEquals("placeholders.no-land", key); return "Geen land";
        }));
    }
}
