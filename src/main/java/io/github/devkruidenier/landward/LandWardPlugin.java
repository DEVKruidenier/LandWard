package io.github.devkruidenier.landward;

import io.github.devkruidenier.landward.command.LandCommand;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.console.LandWardConsole;
import io.github.devkruidenier.landward.console.LandWardConsole.Tone;
import io.github.devkruidenier.landward.locale.LocaleService;
import io.github.devkruidenier.landward.metrics.MetricsService;
import io.github.devkruidenier.landward.integration.DynmapIntegration;
import io.github.devkruidenier.landward.integration.BlueMapIntegration;
import io.github.devkruidenier.landward.integration.plan.PlanIntegration;
import io.github.devkruidenier.landward.integration.SquaremapIntegration;
import io.github.devkruidenier.landward.integration.Pl3xMapIntegration;
import io.github.devkruidenier.landward.gui.LandMapListener;
import io.github.devkruidenier.landward.gui.LandManagementListener;
import io.github.devkruidenier.landward.gui.LandRenameListener;
import io.github.devkruidenier.landward.protection.ClaimProtectionListener;
import io.github.devkruidenier.landward.startup.StartupReporter;
import io.github.devkruidenier.landward.storage.LandStorage;
import io.github.devkruidenier.landward.storage.StorageException;
import io.github.devkruidenier.landward.storage.mysql.MySqlLandStorage;
import io.github.devkruidenier.landward.storage.sqlite.SqliteLandStorage;
import io.github.devkruidenier.landward.update.UpdateChecker;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class LandWardPlugin extends JavaPlugin {

    private LocaleService localeService;
    private MetricsService metricsService;
    private LandWardConsole console;
    private LandStorage storage;
    private LandManager landManager;
    private DynmapIntegration dynmapIntegration;
    private BlueMapIntegration blueMapIntegration;
    private PlanIntegration planIntegration;
    private SquaremapIntegration squaremapIntegration;
    private Pl3xMapIntegration pl3xMapIntegration;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.console = new LandWardConsole(this);
        this.localeService = new LocaleService(this);
        this.localeService.initialize();

        this.storage = createStorage();
        try {
            storage.initialize();
            this.landManager = new LandManager(storage);
            landManager.load();
        } catch (StorageException exception) {
            throw new IllegalStateException("LandWard storage could not be initialized", exception);
        }
        this.dynmapIntegration = new DynmapIntegration(this, landManager);
        this.blueMapIntegration = new BlueMapIntegration(this, landManager);
        this.squaremapIntegration = new SquaremapIntegration(this, landManager);
        this.pl3xMapIntegration = new Pl3xMapIntegration(this, landManager);
        blueMapIntegration.sync();
        dynmapIntegration.sync();
        squaremapIntegration.sync();
        pl3xMapIntegration.sync();
        if (getConfig().getBoolean("integrations.plan.enabled", true)
                && getServer().getPluginManager().getPlugin("Plan") != null) {
            this.planIntegration = new PlanIntegration(this, landManager);
            planIntegration.register();
        }

        LandCommand landCommand = new LandCommand(this, landManager);
        PluginCommand command = getCommand("claim");
        if (command == null) {
            throw new IllegalStateException("The claim command is missing from plugin.yml");
        }
        command.setExecutor(landCommand);
        command.setTabCompleter(landCommand);
        getServer().getPluginManager().registerEvents(new ClaimProtectionListener(this, landManager), this);
        getServer().getPluginManager().registerEvents(new LandMapListener(), this);
        LandRenameListener renameListener = new LandRenameListener(this, landManager);
        getServer().getPluginManager().registerEvents(renameListener, this);
        getServer().getPluginManager().registerEvents(new LandManagementListener(this, landManager, renameListener), this);

        this.metricsService = new MetricsService(this);
        this.metricsService.initialize();

        new StartupReporter(this).log(landManager.totalClaimCount());
        console.ready(localeService.console("plugin.enabled"));

        new UpdateChecker(this).check();
    }

    @Override
    public void onDisable() {
        if (localeService != null) {
            console.info(localeService.console("plugin.disabled"), Tone.WARNING);
        }
        if (planIntegration != null) {
            planIntegration.unregister();
        }
        if (storage != null) {
            try {
                storage.close();
            } catch (StorageException exception) {
                getLogger().log(java.util.logging.Level.SEVERE, "Could not close LandWard storage", exception);
            }
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

    public LandStorage storage() {
        return storage;
    }

    public void syncMapIntegrations() {
        if (blueMapIntegration != null) blueMapIntegration.sync();
        if (dynmapIntegration != null) dynmapIntegration.sync();
        if (squaremapIntegration != null) squaremapIntegration.sync();
        if (pl3xMapIntegration != null) pl3xMapIntegration.sync();
        if (planIntegration != null) planIntegration.refresh();
    }

    public void refreshAnalyticsIntegration() {
        if (planIntegration != null) planIntegration.refresh();
    }

    private LandStorage createStorage() {
        String type = getConfig().getString("storage.type", "sqlite").trim();
        if (type.equalsIgnoreCase("sqlite")) {
            return new SqliteLandStorage(getDataFolder().toPath().resolve("landward.db"));
        }
        if (type.equalsIgnoreCase("mysql")) {
            return new MySqlLandStorage(
                    getConfig().getString("storage.mysql.host", "localhost"),
                    getConfig().getInt("storage.mysql.port", 3306),
                    getConfig().getString("storage.mysql.database", "landward"),
                    getConfig().getString("storage.mysql.username", "minecraft"),
                    getConfig().getString("storage.mysql.password", ""),
                    getConfig().getBoolean("storage.mysql.ssl", false),
                    getConfig().getInt("storage.mysql.pool-size", 10)
            );
        }
        throw new IllegalArgumentException("Unsupported storage.type: " + type + ". Use sqlite or mysql.");
    }
}
