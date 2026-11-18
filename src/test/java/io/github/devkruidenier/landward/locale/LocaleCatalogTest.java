package io.github.devkruidenier.landward.locale;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocaleCatalogTest {
    @Test void recognizesAllCanonicalClientLocalesRegardlessOfCaseOrSeparator() {
        assertEquals(32, LocaleCatalog.LOCALES.size());
        assertEquals(32, LocaleCatalog.supported().size());
        for (String locale : LocaleCatalog.LOCALES) {
            assertEquals(locale, LocaleCatalog.forClient(locale.toLowerCase().replace('_','-'), "en_US"));
        }
    }
    @Test void javaAndBedrockBokmalShareOneCatalogWithoutOverridingNynorsk() {
        assertEquals("nb_NO", LocaleCatalog.forClient("no_no", "en_US"));
        assertEquals("nb_NO", LocaleCatalog.configured("nb_NO"));
        assertEquals("nb_NO", LocaleCatalog.configured("no-NO"));
        assertEquals("nn_NO", LocaleCatalog.forClient("nn_no", "en_US"));
        assertEquals("fil_PH", LocaleCatalog.forClient("fil_ph", "en_US"));
    }
    @Test void unsupportedClientsUseTheServerDefaultAndInvalidDefaultsUseEnglish() {
        assertEquals("fi_FI", LocaleCatalog.forClient("xx_YY", "fi_FI"));
        assertEquals("fi_FI", LocaleCatalog.forClient(null, "fi_FI"));
        assertEquals("en_US", LocaleCatalog.forClient("xx_YY", "private.server.example"));
        assertEquals("en_US", LocaleCatalog.configured(null));
    }
}
