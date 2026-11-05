package io.github.devkruidenier.landward.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract tests for the configured PlaceholderAPI combat tag integrations. */
final class CombatTagPlaceholderContractTest {

    @Test
    void recognizesBooleanValuesFromCombatLogXAndDeluxeCombat() {
        assertTrue(CombatTagService.activeValue("true", "%combatlogx_in_combat%"));
        assertTrue(CombatTagService.activeValue("YES", "%deluxecombat_in_combat%"));
        assertFalse(CombatTagService.activeValue("false", "%combatlogx_in_combat%"));
        assertFalse(CombatTagService.activeValue("no", "%deluxecombat_in_combat%"));
    }

    @Test
    void recognizesRemainingPvpManagerCombatTime() {
        assertTrue(CombatTagService.activeValue("15", "%pvpmanager_combat_timeleft%"));
        assertTrue(CombatTagService.activeValue("0.5", "%pvpmanager_combat_timeleft%"));
        assertFalse(CombatTagService.activeValue("0", "%pvpmanager_combat_timeleft%"));
        assertFalse(CombatTagService.activeValue("-1", "%pvpmanager_combat_timeleft%"));
    }

    @Test
    void recognizesAnEternalCombatOpponentButNotItsInactiveValue() {
        assertTrue(CombatTagService.activeValue("Kruidenier", "%eternalcombat_opponent%"));
        assertFalse(CombatTagService.activeValue("N/A", "%eternalcombat_opponent%"));
        assertFalse(CombatTagService.activeValue("none", "%eternalcombat_opponent%"));
    }

    @Test
    void ignoresAnUnresolvedPlaceholder() {
        assertFalse(CombatTagService.activeValue("%combatlogx_in_combat%", "%combatlogx_in_combat%"));
        assertFalse(CombatTagService.activeValue(null, "%combatlogx_in_combat%"));
    }
}
