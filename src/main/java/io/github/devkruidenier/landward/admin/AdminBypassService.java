package io.github.devkruidenier.landward.admin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the administrator protection bypass deliberately temporary. Possessing the
 * permission only permits toggling it; it never silently grants the bypass.
 */
public final class AdminBypassService {
    private final Set<UUID> enabledPlayers = ConcurrentHashMap.newKeySet();

    /** Toggles bypass for the current login session and returns its new state. */
    public boolean toggle(UUID playerId) {
        if (!enabledPlayers.add(playerId)) {
            enabledPlayers.remove(playerId);
            return false;
        }
        return true;
    }

    public boolean isEnabled(UUID playerId) {
        return enabledPlayers.contains(playerId);
    }

    public void clear(UUID playerId) {
        enabledPlayers.remove(playerId);
    }
}
