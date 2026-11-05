package io.github.devkruidenier.landward.model;

/**
 * Behaviour switches for members inside one player-owned land.  These never
 * grant access to visitors; a player must already be the owner or a member.
 */
public record LandProtectionSettings(
        boolean memberPvp,
        boolean hostileMobKills,
        boolean passiveMobKills,
        boolean hostileMobSpawns,
        boolean doors,
        boolean interactions
) {
    public static final LandProtectionSettings DEFAULT = new LandProtectionSettings(
            false, true, true, true, true, true
    );

    public LandProtectionSettings with(Setting setting, boolean enabled) {
        return switch (setting) {
            case MEMBER_PVP -> new LandProtectionSettings(enabled, hostileMobKills, passiveMobKills, hostileMobSpawns, doors, interactions);
            case HOSTILE_MOB_KILLS -> new LandProtectionSettings(memberPvp, enabled, passiveMobKills, hostileMobSpawns, doors, interactions);
            case PASSIVE_MOB_KILLS -> new LandProtectionSettings(memberPvp, hostileMobKills, enabled, hostileMobSpawns, doors, interactions);
            case HOSTILE_MOB_SPAWNS -> new LandProtectionSettings(memberPvp, hostileMobKills, passiveMobKills, enabled, doors, interactions);
            case DOORS -> new LandProtectionSettings(memberPvp, hostileMobKills, passiveMobKills, hostileMobSpawns, enabled, interactions);
            case INTERACTIONS -> new LandProtectionSettings(memberPvp, hostileMobKills, passiveMobKills, hostileMobSpawns, doors, enabled);
        };
    }

    public enum Setting {
        MEMBER_PVP, HOSTILE_MOB_KILLS, PASSIVE_MOB_KILLS, HOSTILE_MOB_SPAWNS, DOORS, INTERACTIONS
    }
}
