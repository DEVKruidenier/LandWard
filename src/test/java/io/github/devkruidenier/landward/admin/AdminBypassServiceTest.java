package io.github.devkruidenier.landward.admin;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminBypassServiceTest {

    @Test
    void bypassIsDisabledByDefaultAndToggleIsPerPlayer() {
        AdminBypassService service = new AdminBypassService();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertFalse(service.isEnabled(first));
        assertTrue(service.toggle(first));
        assertTrue(service.isEnabled(first));
        assertFalse(service.isEnabled(second));
        assertFalse(service.toggle(first));
        assertFalse(service.isEnabled(first));
    }

    @Test
    void clearDisablesAnEnabledBypass() {
        AdminBypassService service = new AdminBypassService();
        UUID player = UUID.randomUUID();

        service.toggle(player);
        service.clear(player);

        assertFalse(service.isEnabled(player));
    }
}
