package io.github.devkruidenier.landward.model;

import java.util.Set;
import java.util.EnumSet;

/** Per-land rules apply equally to owners, members and visitors. */
public record EnvironmentSettings(Set<Rule> allowed) {
    public EnvironmentSettings { allowed = Set.copyOf(allowed); }
    public enum Rule {
        FIRE_SPREAD, FALL_DAMAGE, TNT_BLOCK_DAMAGE, OTHER_EXPLOSION_BLOCK_DAMAGE,
        EXPLOSION_PLAYER_DAMAGE, FIRE_PLAYER_DAMAGE, LAVA_PLAYER_DAMAGE,
        MOB_BLOCK_CHANGES, WATER_FLOW, LAVA_FLOW
    }
    /** Legacy explosion/block denial is retained; fire spread denial is intentional. */
    public static EnvironmentSettings legacy() { return defaults(false); }
    public static EnvironmentSettings defaults(boolean safeZone) {
        var rules = EnumSet.of(Rule.WATER_FLOW, Rule.LAVA_FLOW);
        if (!safeZone) rules.addAll(EnumSet.of(Rule.FALL_DAMAGE, Rule.EXPLOSION_PLAYER_DAMAGE,
                Rule.FIRE_PLAYER_DAMAGE, Rule.LAVA_PLAYER_DAMAGE));
        return new EnvironmentSettings(rules);
    }
    public boolean allows(Rule rule) { return allowed.contains(rule); }
    public EnvironmentSettings with(Rule rule, boolean enabled) {
        var rules = EnumSet.noneOf(Rule.class); rules.addAll(allowed);
        if (enabled) rules.add(rule); else rules.remove(rule);
        return new EnvironmentSettings(rules);
    }
}
