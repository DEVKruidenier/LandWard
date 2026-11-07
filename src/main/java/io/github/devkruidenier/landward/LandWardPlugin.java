package io.github.devkruidenier.landward;

import io.github.devkruidenier.landward.command.LandCommand;
import io.github.devkruidenier.landward.command.ClaimAdminCommand;
import io.github.devkruidenier.landward.admin.AdminBypassService;
import io.github.devkruidenier.landward.admin.AdminBypassListener;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.console.LandWardConsole;
import io.github.devkruidenier.landward.console.LandWardConsole.Tone;
import io.github.devkruidenier.landward.locale.LocaleService;
import io.github.devkruidenier.landward.metrics.MetricsService;
import io.github.devkruidenier.landward.integration.BlueMapIntegration;
import io.github.devkruidenier.landward.integration.plan.PlanIntegration;
import io.github.devkruidenier.landward.integration.SquaremapIntegration;
import io.github.devkruidenier.landward.integration.Pl3xMapIntegration;
import io.github.devkruidenier.landward.integration.placeholder.LandWardExpansion;
import io.github.devkruidenier.landward.gui.LandMapListener;
import io.github.devkruidenier.landward.gui.LandManagementListener;
import io.github.devkruidenier.landward.gui.LandRenameListener;
import io.github.devkruidenier.landward.gui.ClaimAdminGui;
import io.github.devkruidenier.landward.gui.ClaimAdminListener;
import io.github.devkruidenier.landward.gui.AdminOwnerChangeListener;
import io.github.devkruidenier.landward.protection.ClaimProtectionListener;
import io.github.devkruidenier.landward.protection.ClaimBoundaryListener;
import io.github.devkruidenier.landward.combat.CombatTagListener;
import io.github.devkruidenier.landward.combat.CombatTagService;
import io.github.devkruidenier.landward.startup.StartupReporter;
import io.github.devkruidenier.landward.storage.LandStorage;
import io.github.devkruidenier.landward.storage.StorageException;
import io.github.devkruidenier.landward.storage.mysql.MySqlLandStorage;
import io.github.devkruidenier.landward.storage.sqlite.SqliteLandStorage;
import io.github.devkruidenier.landward.model.MemberPermissions;
import io.github.devkruidenier.landward.update.UpdateChecker;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.util.Objects;
import java.nio.charset.StandardCharsets;

public final class LandWardPlugin extends JavaPlugin {

    private LocaleService localeService;
    private MetricsService metricsService;
    private LandWardConsole console;
    private LandStorage storage;
    private LandManager landManager;
    private BlueMapIntegration blueMapIntegration;
    private PlanIntegration planIntegration;
    private SquaremapIntegration squaremapIntegration;
    private Pl3xMapIntegration pl3xMapIntegration;
    private CombatTagService combatTags;
    private AdminBypassService adminBypasses;
    private UpdateChecker updateChecker;
    private AdminOwnerChangeListener adminOwnerChangeListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        mergeNewConfigDefaults();

        this.console = new LandWardConsole(this);
        this.localeService = new LocaleService(this);
        this.localeService.initialize();

        this.storage = createStorage(getConfig().getString("storage.type", "sqlite"));
        try {
            storage.initialize();
            this.landManager = new LandManager(storage, playerMemberDefaults(), administrativeMemberDefaults());
            landManager.load();
        } catch (StorageException exception) {
            throw new IllegalStateException("LandWard storage could not be initialized", exception);
        }
        this.blueMapIntegration = new BlueMapIntegration(this, landManager);
        this.squaremapIntegration = new SquaremapIntegration(this, landManager);
        this.pl3xMapIntegration = new Pl3xMapIntegration(this, landManager);
        this.combatTags = new CombatTagService(this);
        this.adminBypasses = new AdminBypassService();
        blueMapIntegration.registerReadinessListener();
        blueMapIntegration.sync();
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
        ClaimAdminCommand claimAdminCommand = new ClaimAdminCommand(this, landManager);
        adminCommand.setExecutor(claimAdminCommand);
        adminCommand.setTabCompleter(claimAdminCommand);
        getServer().getPluginManager().registerEvents(new ClaimProtectionListener(this, landManager, combatTags, adminBypasses), this);
        getServer().getPluginManager().registerEvents(new AdminBypassListener(adminBypasses), this);
        getServer().getPluginManager().registerEvents(new ClaimBoundaryListener(this, landManager), this);
        getServer().getPluginManager().registerEvents(new CombatTagListener(combatTags), this);
        getServer().getPluginManager().registerEvents(new LandMapListener(this, landCommand, landManager), this);
        LandRenameListener renameListener = new LandRenameListener(this, landManager, landCommand);
        getServer().getPluginManager().registerEvents(renameListener, this);
        getServer().getPluginManager().registerEvents(new LandManagementListener(this, landManager, renameListener), this);
        getServer().getPluginManager().registerEvents(new ClaimAdminListener(this, landManager), this);
        this.adminOwnerChangeListener = new AdminOwnerChangeListener(this, landManager);
        getServer().getPluginManager().registerEvents(adminOwnerChangeListener, this);

        this.metricsService = new MetricsService(this);
        this.metricsService.initialize();

        new StartupReporter(this).log(landManager.totalClaimCount());
        console.ready(localeService.console("plugin.enabled"));

        this.updateChecker = new UpdateChecker(this);
        updateChecker.check();
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

    /** Activates a verified migration target without disconnecting players or restarting Paper. */
    public void activateMigratedStorage(LandStorage replacement, String type) throws StorageException {
        LandStorage previous = storage;
        landManager.replaceStorage(replacement);
        storage = replacement;
        getConfig().set("storage.type", type);
        saveConfig();
        syncMapIntegrations();
        if (previous != replacement) {
            try {
                previous.close();
            } catch (StorageException exception) {
                getLogger().warning("Migrated storage is active, but the previous connection could not close: " + exception.getMessage());
            }
        }
    }

    public CombatTagService combatTags() {
        return combatTags;
    }

    public AdminBypassService adminBypasses() {
        return adminBypasses;
    }

    public UpdateChecker updateChecker() {
        return updateChecker;
    }

    public AdminOwnerChangeListener adminOwnerChangeListener() {
        return adminOwnerChangeListener;
    }

    public void syncMapIntegrations() {
        if (blueMapIntegration != null) blueMapIntegration.sync();
        if (squaremapIntegration != null) squaremapIntegration.sync();
        if (pl3xMapIntegration != null) pl3xMapIntegration.sync();
        if (planIntegration != null) planIntegration.refresh();
    }

    /** LandWard is active in every world except explicit disabled-world exceptions. */
    public boolean isLandWorld(World world) {
        java.util.List<String> disabled = getConfig().getStringList("claims.disabled-worlds");
        return !disabled.contains(world.getName());
    }

    /** Persists an administrator's per-world LandWard activation choice. */
    public void setLandWorldEnabled(World world, boolean active) {
        java.util.List<String> disabled = new java.util.ArrayList<>(getConfig().getStringList("claims.disabled-worlds"));
        if (active) {
            disabled.remove(world.getName());
        } else if (!disabled.contains(world.getName())) {
            disabled.add(world.getName());
        }
        getConfig().set("claims.disabled-worlds", disabled);
        applyWorldConfigComments();
        saveConfig();
        syncMapIntegrations();
    }

    public void refreshAnalyticsIntegration() {
        if (planIntegration != null) planIntegration.refresh();
    }

    /** Reloads safe runtime settings without disconnecting players or reopening storage. */
    public void reloadLandWard() {
        reloadConfig();
        mergeNewConfigDefaults();
        localeService.initialize();
        landManager.memberDefaults(playerMemberDefaults(), administrativeMemberDefaults());
        syncMapIntegrations();
    }

    private MemberPermissions playerMemberDefaults() {
        return memberDefaults("player-claims.member-rights");
    }

    private MemberPermissions administrativeMemberDefaults() {
        return memberDefaults("admin-claims.member-rights");
    }

    private MemberPermissions memberDefaults(String path) {
        return new MemberPermissions(
                getConfig().getBoolean(path + ".build", true),
                getConfig().getBoolean(path + ".containers", true),
                getConfig().getBoolean(path + ".entities", true)
        );
    }

    /** Creates an independent configured storage connection for the selected backend. */
    public LandStorage createStorage(String requestedType) {
        String type = requestedType == null ? "sqlite" : requestedType.trim();
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
            File configFile = new File(getDataFolder(), "config.yml");
            // Read the file directly instead of relying on Bukkit's cached
            // configuration instance. This makes additions of an entirely new
            // section reliable during an update as well as during /claimadmin
            // reload.
            YamlConfiguration existing = YamlConfiguration.loadConfiguration(configFile);
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8)
            );
            YamlConfiguration newBaseline = new YamlConfiguration();
            for (String path : bundled.getKeys(true)) {
                if (!bundled.isConfigurationSection(path)) newBaseline.set(path, bundled.get(path));
            }
            File baselineFile = new File(getDataFolder(), ".config-defaults.yml");
            YamlConfiguration previousBaseline = baselineFile.isFile()
                    ? YamlConfiguration.loadConfiguration(baselineFile) : null;
            // Keep values selected by the server owner, but always write the
            // bundled order, grouping and comments. If a value still matches
            // LandWard's previous default, a new official default may replace it.
            // Obsolete keys deliberately disappear instead of accumulating forever.
            for (String path : new java.util.ArrayList<>(bundled.getKeys(true))) {
                if (!bundled.isConfigurationSection(path) && existing.contains(path)) {
                    Object current = existing.get(path);
                    if (previousBaseline == null || !previousBaseline.contains(path)
                            || !Objects.equals(current, previousBaseline.get(path))) {
                        bundled.set(path, current);
                    }
                }
            }
            // A normal update must not leave surprise backup files beside the
            // administrator's configuration. The value merge above preserves
            // deliberate choices while the bundled file restores the structure.
            bundled.save(configFile);
            newBaseline.save(baselineFile);
            reloadConfig();
        } catch (Exception exception) {
            getLogger().warning("Could not merge new LandWard config defaults: " + exception.getMessage());
        }
    }

    private void applyWorldConfigComments() {
        getConfig().setComments("claims.disabled-worlds", java.util.List.of(
                "World names where LandWard is fully inactive. Every other world is active.",
                "Example world: LandWard is disabled in this world until you remove it from this list."
        ));
    }
}
