package io.github.devkruidenier.landward;

import io.github.devkruidenier.landward.command.LandCommand;
import io.github.devkruidenier.landward.console.LandWardConsole;
import io.github.devkruidenier.landward.console.LandWardConsole.Tone;
import io.github.devkruidenier.landward.locale.LocaleService;
import io.github.devkruidenier.landward.metrics.MetricsService;
import io.github.devkruidenier.landward.startup.StartupReporter;
import io.github.devkruidenier.landward.update.UpdateChecker;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class LandWardPlugin extends JavaPlugin {

    private LocaleService localeService;
    private MetricsService metricsService;
    private LandWardConsole console;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.console = new LandWardConsole(this);
        this.localeService = new LocaleService(this);
        this.localeService.initialize();

        LandCommand landCommand = new LandCommand(this);
        PluginCommand command = getCommand("land");
        if (command == null) {
            throw new IllegalStateException("The land command is missing from plugin.yml");
        }
        command.setExecutor(landCommand);
        command.setTabCompleter(landCommand);

        this.metricsService = new MetricsService(this);
        this.metricsService.initialize();

        new StartupReporter(this).log(0);
        console.info(localeService.console("plugin.enabled"), Tone.SUCCESS);

        new UpdateChecker(this).check();
    }

    @Override
    public void onDisable() {
        if (localeService != null) {
            console.info(localeService.console("plugin.disabled"), Tone.WARNING);
        }
    }

    public LocaleService localeService() {
        return localeService;
    }

    public MetricsService metricsService() {
        return metricsService;
    }

    public LandWardConsole console() {
        return console;
    }
}
