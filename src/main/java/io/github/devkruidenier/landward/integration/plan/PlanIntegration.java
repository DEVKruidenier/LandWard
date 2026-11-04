package io.github.devkruidenier.landward.integration.plan;

import com.djrapitops.plan.capability.CapabilityService;
import com.djrapitops.plan.extension.Caller;
import com.djrapitops.plan.extension.ExtensionService;
import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.console.LandWardConsole.Tone;
import org.bukkit.entity.Player;

import java.util.Optional;

/** Optional bridge to Plan's DataExtension API. */
public final class PlanIntegration {

    private static final String DATA_EXTENSION_VALUES = "DATA_EXTENSION_VALUES";

    private final LandWardPlugin plugin;
    private final LandWardPlanExtension extension;
    private Caller caller;

    public PlanIntegration(LandWardPlugin plugin, LandManager landManager) {
        this.plugin = plugin;
        this.extension = new LandWardPlanExtension(landManager);
    }

    public void register() {
        try {
            CapabilityService capabilities = CapabilityService.getInstance();
            if (!capabilities.hasCapability(DATA_EXTENSION_VALUES)) {
                plugin.console().info("Plan detected, but its DataExtension API is unavailable.", Tone.WARNING);
                return;
            }
            registerExtensionSafely();
            capabilities.registerEnableListener(enabled -> {
                if (enabled) plugin.getServer().getScheduler().runTask(plugin, this::registerExtensionSafely);
            });
        } catch (NoClassDefFoundError ignored) {
            // Plan is intentionally optional: never prevent LandWard from enabling.
        } catch (IllegalStateException exception) {
            plugin.console().info("Plan detected but is not ready yet; LandWard statistics were not registered.", Tone.WARNING);
        }
    }

    public void refresh() {
        if (caller == null) return;
        if (!plugin.getServer().isPrimaryThread()) {
            plugin.getServer().getScheduler().runTask(plugin, this::refresh);
            return;
        }
        caller.updateServerData();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            caller.updatePlayerData(player.getUniqueId(), player.getName());
        }
    }

    public void unregister() {
        try {
            ExtensionService.getInstance().unregister(extension);
        } catch (NoClassDefFoundError | IllegalStateException ignored) {
            // Plan may already have been disabled before LandWard.
        }
    }

    private void registerExtensionSafely() {
        try {
            Optional<Caller> registered = ExtensionService.getInstance().register(extension);
            caller = registered.orElse(null);
            if (caller != null) {
                plugin.console().info("Plan integration enabled: LandWard statistics are available in its web panel.", Tone.SUCCESS);
                refresh();
            }
        } catch (IllegalStateException exception) {
            plugin.console().info("Plan detected but is not ready yet; LandWard statistics were not registered.", Tone.WARNING);
        }
    }
}
