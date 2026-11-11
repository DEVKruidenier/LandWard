package io.github.devkruidenier.landward.gui;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

final class GuiHintsTest {
    @Test
    void worldDisabledPvpExplainsTheBlockWithoutPromisingAToggle() {
        var lore = GuiHints.pvpUnavailable(Function.identity());
        assertTrue(lore.contains("gui.hint-pvp-world-disabled"));
        assertFalse(lore.contains("gui.management-toggle"));
    }

    @Test
    void playerPvpDoesNotSuggestThatVisitorsCanFight() {
        var player = GuiHints.setting("IRON_SWORD", false, null, Function.identity());
        var safeZone = GuiHints.setting("IRON_SWORD", true, "public", Function.identity());
        assertTrue(player.contains("gui.hint-pvp-members"));
        assertFalse(player.contains("gui.hint-pvp-public"));
        assertTrue(safeZone.contains("gui.hint-pvp-public"));
        assertTrue(player.contains("gui.hint-world-pvp"));
        assertTrue(safeZone.contains("gui.hint-world-pvp"));
    }

    @Test
    void entityDescriptionsExplainOtherProtectionsAndIndividualScope() {
        var lore = GuiHints.setting("LEAD", true, "member", Function.identity());
        assertTrue(lore.contains("gui.hint-entities-animals"));
        assertTrue(lore.contains("gui.hint-entities-objects"));
        assertTrue(lore.contains("gui.hint-entities-items"));
        assertTrue(lore.contains("gui.hint-other-rules"));
        assertTrue(lore.contains("gui.hint-scope-member"));
        assertFalse(lore.contains("gui.hint-scope-public"));
    }

    @Test
    void everySettingDescriptionResolvesFromEveryLanguageCatalog() throws Exception {
        for (String locale : List.of("en_US", "nl_NL", "de_DE", "fr_FR", "es_ES", "it_IT", "pt_BR",
                "pl_PL", "tr_TR", "ru_RU", "zh_CN", "ja_JP", "ko_KR")) {
            try (var input = getClass().getResourceAsStream("/lang/" + locale + ".yml")) {
                assertNotNull(input);
                Map<?, ?> catalog = new Yaml().load(new InputStreamReader(input, StandardCharsets.UTF_8));
                Map<?, ?> gui = (Map<?, ?>) catalog.get("gui");
                Function<String, String> translate = key -> {
                    Object text = gui.get(key.substring("gui.".length()));
                    assertTrue(text instanceof String && !((String) text).isBlank(), locale + ": " + key);
                    return (String) text;
                };
                GuiHints.pvpUnavailable(translate);
                translate.apply("gui.management-unavailable");
                for (String material : List.of("IRON_SWORD", "IRON_PICKAXE", "CHEST",
                        "OAK_DOOR", "OAK_SIGN", "LEAD", "BEEF", "ZOMBIE_HEAD", "CREEPER_SPAWN_EGG")) {
                    GuiHints.setting(material, false, "members", translate);
                    GuiHints.setting(material, true, "public", translate);
                    GuiHints.setting(material, true, "member", translate);
                }
            }
        }
    }
}
