package io.github.devkruidenier.landward;

import io.github.devkruidenier.landward.locale.LocaleService;
import org.bukkit.plugin.java.JavaPlugin;

public final class LandWardPlugin extends JavaPlugin {

    private LocaleService localeService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.localeService = new LocaleService(this);
        this.localeService.initialize();

        getLogger().info(localeService.console("plugin.enabled"));
    }

    @Override
    public void onDisable() {
        if (localeService != null) {
            getLogger().info(localeService.console("plugin.disabled"));
        }
    }

    public LocaleService localeService() {
        return localeService;
    }
}
