package io.github.devkruidenier.landward.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Shared, localized descriptions; never used to identify or execute an action. */
final class GuiHints {
    private GuiHints() {}

    static List<String> pvpUnavailable(Function<String, String> message) {
        return List.of(message.apply("gui.hint-pvp-world-disabled"), message.apply("gui.hint-world-pvp"));
    }

    static List<String> setting(String material, boolean safeZone, String scope, Function<String, String> message) {
        List<String> keys = switch (material) {
            case "IRON_SWORD" -> List.of(safeZone ? "pvp-public" : "pvp-members", "world-pvp");
            case "IRON_PICKAXE" -> List.of("build");
            case "CHEST" -> List.of("containers");
            case "OAK_DOOR" -> List.of("doors");
            case "OAK_SIGN" -> List.of("interactions");
            case "LEAD" -> List.of("entities-animals", "entities-objects", "entities-items", "other-rules");
            case "BEEF" -> List.of("passive-kills");
            case "ZOMBIE_HEAD" -> List.of("hostile-kills");
            case "CREEPER_SPAWN_EGG" -> List.of("hostile-spawns");
            default -> throw new IllegalArgumentException("No setting description for " + material);
        };
        List<String> lore = new ArrayList<>();
        keys.forEach(key -> lore.add(message.apply("gui.hint-" + key)));
        if (scope != null) lore.add(message.apply("gui.hint-scope-" + scope));
        lore.add(message.apply("gui.management-toggle"));
        return lore;
    }
}
