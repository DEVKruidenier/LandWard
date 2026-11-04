package io.github.devkruidenier.landward.combat;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/** Tracks successful player-versus-player hits outside LandWard's protection listener. */
public final class CombatTagListener implements Listener {

    private final CombatTagService combatTags;

    public CombatTagListener(CombatTagService combatTags) {
        this.combatTags = combatTags;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = event.getDamager() instanceof Player player ? player
                : event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player ? player
                : null;
        if (attacker != null && !attacker.equals(victim) && event.getFinalDamage() > 0.0D) {
            combatTags.tag(attacker, victim);
        }
    }
}
