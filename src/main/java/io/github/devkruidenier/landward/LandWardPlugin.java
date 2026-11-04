package io.github.devkruidenier.landward;

import io.github.devkruidenier.landward.command.LandCommand;
import io.github.devkruidenier.landward.command.ClaimAdminCommand;
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
import io.github.devkruidenier.landward.integration.placeholder.LandWardExpansion;
import io.github.devkruidenier.landward.gui.LandMapListener;
import io.github.devkruidenier.landward.gui.LandManagementListener;
import io.github.devkruidenier.landward.gui.LandRenameListener;
import io.github.devkruidenier.landward.protection.ClaimProtectionListener;
import io.github.devkruidenier.landward.protection.ClaimBoundaryListener;
import io.github.devkruidenier.landward.combat.CombatTagListener;
import io.github.devkruidenier.landward.combat.CombatTagService;
import io.github.devkruidenier.landward.startup.StartupReporter;
import io.github.devkruidenier.landward.storage.LandStorage;
import io.github.devkruidenier.landward.storage.StorageException;
import io.github.devkruidenier.landward.storage.mysql.MySqlLandStorage;
import io.github.devkruidenier.landward.storage.sqlite.SqliteLandStorage;
import io.github.devkruidenier.landward.update.UpdateChecker;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

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
    private CombatTagService combatTags;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        mergeNewConfigDefaults();

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
        this.combatTags = new CombatTagService(this);
        blueMapIntegration.registerReadinessListener();
        blueMapIntegration.sync();
        dynmapIntegration.sync();
        squaremapIntegration.sync();
        pl3xMapIntegration.sync();
        // Map plugins can finish their own asynchronous API initialization after
        // Bukkit has enabled LandWard. A second, delayed pass makes the optional
        // marker layers reliable without delaying server startup.
        getServer().getScheduler().runTaskLater(this, this::syncMapIntegrations, 20L * 5L);
        if (getConfig().getBoolean("integrations.plan.enabled", true)
                && getServer().getPluginManager().getPlugin("Plan") != null) {
            this.planIntegration = new PlanIntegration(this, landManager);
            planIntegration.register();
        }
        if (getConfig().getBoolean("integrations.placeholderapi.enabled", true)
                && getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new LandWardExpansion(this, landManager).register();
            console.info("PlaceholderAPI integration enabled: use %landward_<placeholder>%.", Tone.SUCCESS);
        }

        LandCommand landCommand = new LandCommand(this, landManager);
        PluginCommand command = getCommand("claim");
        if (command == null) {
            throw new IllegalStateException("The claim command is missing from plugin.yml");
        }
        command.setExecutor(landCommand);
        command.setTabCompleter(landCommand);
        PluginCommand adminCommand = getCommand("claimadmin");
        if (adminCommand == null) throw new IllegalStateException("The claimadmin command is missing from plugin.yml");
        adminCommand.setExecutor(new ClaimAdminCommand(this, landManager));
        getServer().getPluginManager().registerEvents(new ClaimProtectionListener(this, landManager, combatTags), this);
        getServer().getPluginManager().registerEvents(new ClaimBoundaryListener(this, landManager), this);
        getServer().getPluginManager().registerEvents(new CombatTagListener(combatTags), this);
        getServer().getPluginManager().registerEvents(new LandMapListener(this, landCommand, landManager), this);
        LandRenameListener renameListener = new LandRenameListener(this, landManager, landCommand);
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
        if (blueMapIntegration != null) {
            blueMapIntegration.unregisterReadinessListener();
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

    public CombatTagService combatTags() {
        return combatTags;
    }

    public void syncMapIntegrations() {
        if (blueMapIntegration != null) blueMapIntegration.sync();
        if (dynmapIntegration != null) dynmapIntegration.sync();
        if (squaremapIntegration != null) squaremapIntegration.sync();
        if (pl3xMapIntegration != null) pl3xMapIntegration.sync();
        if (planIntegration != null) planIntegration.refresh();
    }

    /** Returns whether LandWard is active for this world. An empty allow-list permits all worlds. */
    public boolean isLandWorld(World world) {
        java.util.List<String> enabled = getConfig().getStringList("claims.enabled-worlds");
        java.util.List<String> disabled = getConfig().getStringList("claims.disabled-worlds");
        return !disabled.contains(world.getName()) && (enabled.isEmpty() || enabled.contains(world.getName()));
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

    /**
     * Adds configuration keys introduced by a newer LandWard version without replacing
     * values that a server owner has already chosen.
     */
    private void mergeNewConfigDefaults() {
        try (InputStream resource = getResource("config.yml")) {
            if (resource == null) return;
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8)
            );
            getConfig().setDefaults(bundled);
            getConfig().options().copyDefaults(true);
            saveConfig();
        } catch (Exception exception) {
            getLogger().warning("Could not merge new LandWard config defaults: " + exception.getMessage());
        }
    }
}
