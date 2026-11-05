package io.github.devkruidenier.landward.admin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Clears a temporary protection bypass as soon as its player leaves the server. */
public final class AdminBypassListener implements Listener {
    private final AdminBypassService bypasses;

    public AdminBypassListener(AdminBypassService bypasses) {
        this.bypasses = bypasses;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        bypasses.clear(event.getPlayer().getUniqueId());
    }
}
