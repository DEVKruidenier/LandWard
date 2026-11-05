package io.github.devkruidenier.landward.combat;

import io.github.devkruidenier.landward.LandWardPlugin;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps LandWard's protection aware of PvP combat without taking ownership of combat logging.
 * External combat taggers are queried through their documented PlaceholderAPI values when
 * PlaceholderAPI is available; the small internal tracker is a reliable fallback.
 */
public final class CombatTagService {

    private static final String PLACEHOLDER_API = "me.clip.placeholderapi.PlaceholderAPI";
    private final LandWardPlugin plugin;
    private final Map<UUID, Long> localTags = new ConcurrentHashMap<>();

    public CombatTagService(LandWardPlugin plugin) {
        this.plugin = plugin;
    }

    public void tag(Player first, Player second) {
        if (!enabled() || !plugin.getConfig().getBoolean("integrations.combat-tag.internal-tracker.enabled", true)) return;
        long expiresAt = System.currentTimeMillis() + (1000L * Math.max(1,
                plugin.getConfig().getInt("integrations.combat-tag.internal-tracker.duration-seconds", 15)));
        localTags.put(first.getUniqueId(), expiresAt);
        localTags.put(second.getUniqueId(), expiresAt);
    }

    public boolean isTagged(Player player) {
        if (!enabled()) return false;
        Long expiresAt = localTags.get(player.getUniqueId());
        if (expiresAt != null) {
            if (expiresAt > System.currentTimeMillis()) return true;
            localTags.remove(player.getUniqueId(), expiresAt);
        }
        return externalTag(player);
    }

    /** PvP protection is relaxed only when both combatants are actively tagged. */
    public boolean mayFightInClaim(Player attacker, Player victim) {
        return enabled()
                && plugin.getConfig().getBoolean("integrations.combat-tag.claim-pvp-bypass", true)
                && isTagged(attacker)
                && isTagged(victim);
    }

    public boolean blocksClaimAbandon(Player player) {
        return enabled()
                && plugin.getConfig().getBoolean("integrations.combat-tag.block-claim-abandon", true)
                && isTagged(player);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("integrations.combat-tag.enabled", true);
    }

    private boolean externalTag(Player player) {
        if (!plugin.getConfig().getBoolean("integrations.combat-tag.external-placeholderapi.enabled", true)
                || plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return false;
        }
        for (String placeholder : plugin.getConfig().getStringList("integrations.combat-tag.external-placeholderapi.placeholders")) {
            if (placeholder.isBlank()) continue;
            String value = parsePlaceholder(player, placeholder);
            if (activeValue(value, placeholder)) return true;
        }
        return false;
    }

    private String parsePlaceholder(Player player, String placeholder) {
        try {
            Class<?> api = Class.forName(PLACEHOLDER_API);
            Method method = api.getMethod("setPlaceholders", Player.class, String.class);
            Object result = method.invoke(null, player, placeholder);
            return result instanceof String value ? value : placeholder;
        } catch (ReflectiveOperationException ignored) {
            return placeholder;
        }
    }

    /**
     * Interprets a configured external combat placeholder without coupling the
     * integration to any combat plugin's API. Package-visible for the contract
     * tests that cover the supported PlaceholderAPI value shapes.
     */
    static boolean activeValue(String value, String original) {
        if (value == null || value.equalsIgnoreCase(original)) return false;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("true") || normalized.equals("yes") || normalized.equals("in combat") || normalized.equals("combat")) {
            return true;
        }
        try {
            return Double.parseDouble(normalized) > 0.0D;
        } catch (NumberFormatException ignored) {
            // EternalCombat exposes the opponent name: any non-empty, non-N/A value means combat.
            return !normalized.isBlank() && !normalized.equals("false") && !normalized.equals("no")
                    && !normalized.equals("n/a") && !normalized.equals("none");
        }
    }
}
