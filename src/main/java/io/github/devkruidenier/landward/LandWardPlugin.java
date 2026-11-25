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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class LandWardPlugin extends JavaPlugin {

    private io.github.devkruidenier.landward.platform.PlatformScheduler scheduler;
    public io.github.devkruidenier.landward.platform.PlatformScheduler scheduler() { return scheduler; }
    private final io.github.devkruidenier.landward.platform.PlayerSnapshots playerSnapshots = new io.github.devkruidenier.landward.platform.PlayerSnapshots();
    public io.github.devkruidenier.landward.platform.PlayerSnapshots playerSnapshots() { return playerSnapshots; }
    private final Object configLock = new Object();
    private volatile org.bukkit.configuration.file.FileConfiguration runtimeConfig;
    @Override public org.bukkit.configuration.file.FileConfiguration getConfig() {
        var current = runtimeConfig; return current == null ? super.getConfig() : current;
    }
    @Override public void reloadConfig() {
        synchronized (configLock) { super.reloadConfig(); runtimeConfig = super.getConfig(); }
    }
    /** Publish a fresh configuration: region readers never observe an in-place YAML mutation. */
    public void setConfigValue(String path, Object value) {
        synchronized (configLock) {
            var copy = new YamlConfiguration();
            try {
                copy.loadFromString(getConfig().saveToString());
                copy.set(path, value);
                copy.save(new File(getDataFolder(), "config.yml"));
                runtimeConfig = copy;
            } catch (Exception error) { throw new IllegalStateException("Could not persist LandWard configuration", error); }
        }
    }
    public void toggleConfigValue(String path) {
        synchronized (configLock) { setConfigValue(path, !getConfig().getBoolean(path, false)); }
    }
    private io.github.devkruidenier.landward.platform.PlayerMessages messages;
    public io.github.devkruidenier.landward.platform.PlayerMessages messages() { return messages; }
    private LocaleService localeService;
    private io.github.devkruidenier.landward.blockprotection.ProtectionService blockProtections;
    private io.github.devkruidenier.landward.blockprotection.ProtectionGui blockProtectionGui;
    private io.github.devkruidenier.landward.blockprotection.ProtectionSelectionManager blockSelection;
    public io.github.devkruidenier.landward.blockprotection.ProtectionService blockProtections() { return blockProtections; }
    public io.github.devkruidenier.landward.blockprotection.ProtectionGui blockProtectionGui() { return blockProtectionGui; }
    private MetricsService metricsService;
    private LandWardConsole console;
    private volatile LandStorage storage;
    private LandManager landManager;
    private BlueMapIntegration blueMapIntegration;
    private PlanIntegration planIntegration;
    private SquaremapIntegration squaremapIntegration;
    private Pl3xMapIntegration pl3xMapIntegration;
    private CombatTagService combatTags;
    private AdminBypassService adminBypasses;
    private UpdateChecker updateChecker;
    private AdminOwnerChangeListener adminOwnerChangeListener;
    private io.github.devkruidenier.landward.gui.LandFeaturesGui landFeaturesGui;
    public io.github.devkruidenier.landward.gui.LandFeaturesGui landFeaturesGui() { return landFeaturesGui; }
    private final io.github.devkruidenier.landward.integration.WorldGuardIntegration pvpWorldGuard =
            new io.github.devkruidenier.landward.integration.WorldGuardIntegration(this);

    public boolean pvpAllowed(org.bukkit.Location location) {
        return io.github.devkruidenier.landward.protection.PvpAvailability.allowed(
                location.getWorld() != null && location.getWorld().getPVP(), () -> pvpWorldGuard.blocksPvp(location));
    }

    @Override
    public void onEnable() {
        scheduler = new io.github.devkruidenier.landward.platform.PlatformScheduler(this);
        saveDefaultConfig();
        mergeNewConfigDefaults();

        this.console = new LandWardConsole(this);
        this.localeService = new LocaleService(this);
        this.localeService.initialize();
        this.messages = new io.github.devkruidenier.landward.platform.PlayerMessages(this);
        getServer().getPluginManager().registerEvents(playerSnapshots, this);
        getServer().getOnlinePlayers().forEach(player -> scheduler.player(player, () -> playerSnapshots.capture(player)));

        this.storage = createStorage(getConfig().getString("storage.type", "sqlite"));
        try {
            storage.initialize();
            this.landManager = new LandManager(storage, playerMemberDefaults(), administrativeMemberDefaults());
            landManager.load();
            applyEnvironmentDefaults();
        } catch (StorageException exception) {
            if (getConfig().getBoolean("block-protection.enabled", false)) {
                // With no verifiable snapshot, no location can safely be assumed unprotected.
                // Keep the listener enabled instead of silently opening previously locked storage.
                landManager = new LandManager(storage, playerMemberDefaults(), administrativeMemberDefaults());
                blockProtections = new io.github.devkruidenier.landward.blockprotection.ProtectionService(this, landManager);
                blockProtections.storageUnavailable();
                getServer().getPluginManager().registerEvents(new io.github.devkruidenier.landward.blockprotection.ProtectionListener(this,landManager,blockProtections),this);
                org.bukkit.command.CommandExecutor unavailable=(sender,command,label,args)->{
                    sender.sendMessage(localeService.message(sender instanceof org.bukkit.entity.Player player ? localeService.localeFor(player) : getConfig().getString("language.console", "en_US"),"general.storage-error"));return true;
                };
                if(getCommand("claim")!=null)getCommand("claim").setExecutor(unavailable);
                if(getCommand("claimadmin")!=null)getCommand("claimadmin").setExecutor(unavailable);
                getLogger().log(java.util.logging.Level.SEVERE,"LandWard storage unavailable: configured claim worlds are temporarily read-only. Repair storage and restart; personal protection is not silently disabled.",exception);
                return;
            }
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
        scheduler.globalLater(this::syncMapIntegrations, 20L * 5L);
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

        blockProtections = new io.github.devkruidenier.landward.blockprotection.ProtectionService(this, landManager);
        blockProtectionGui = new io.github.devkruidenier.landward.blockprotection.ProtectionGui(this, landManager, blockProtections);
        blockSelection = new io.github.devkruidenier.landward.blockprotection.ProtectionSelectionManager(this, landManager, blockProtections, blockProtectionGui);
        blockProtectionGui.selection(blockSelection);
        getServer().getPluginManager().registerEvents(blockSelection, this);
        getServer().getPluginManager().registerEvents(blockProtectionGui, this);
        getServer().getPluginManager().registerEvents(new io.github.devkruidenier.landward.blockprotection.ProtectionListener(this, landManager, blockProtections), this);

        LandCommand landCommand = new LandCommand(this, landManager);
        this.landFeaturesGui = new io.github.devkruidenier.landward.gui.LandFeaturesGui(this, landManager);
        getServer().getPluginManager().registerEvents(landFeaturesGui, this);
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
        getServer().getPluginManager().registerEvents(new io.github.devkruidenier.landward.gui.GuiCleanupListener(), this);
        this.adminOwnerChangeListener = new AdminOwnerChangeListener(this, landManager);
        getServer().getPluginManager().registerEvents(adminOwnerChangeListener, this);

        this.metricsService = new MetricsService(this, landManager);
        this.metricsService.initialize();

        new StartupReporter(this).log(landManager.totalClaimCount());
        console.ready(localeService.console("plugin.enabled"));

        this.updateChecker = new UpdateChecker(this);
        updateChecker.check();
        getServer().getPluginManager().registerEvents(new io.github.devkruidenier.landward.update.UpdateNotifications(this), this);
    }

    @Override
    public void onDisable() {
        if (blockSelection != null) blockSelection.clear();
        if (blockProtections != null) blockProtections.close();
        if (landFeaturesGui != null) landFeaturesGui.shutdown();
        if (scheduler != null) scheduler.close();
        playerSnapshots.clear();
        if (metricsService != null) metricsService.shutdown();
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
                synchronized (landManager == null ? this : landManager) { storage.close(); }
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
        setConfigValue("storage.type", type);
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
        scheduler.global(this::syncMapIntegrationsNow);
    }

    private void syncMapIntegrationsNow() {
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
        synchronized (configLock) { setLandWorldEnabledLocked(world, active); }
    }

    private void setLandWorldEnabledLocked(World world, boolean active) {
        java.util.List<String> disabled = new java.util.ArrayList<>(getConfig().getStringList("claims.disabled-worlds"));
        if (active) {
            disabled.remove(world.getName());
        } else if (!disabled.contains(world.getName())) {
            disabled.add(world.getName());
        }
        setConfigValue("claims.disabled-worlds", disabled);
        syncMapIntegrations();
    }

    public void refreshAnalyticsIntegration() {
        if (planIntegration != null) planIntegration.refresh();
    }

    /** Reloads safe runtime settings without disconnecting players or reopening storage. */
    public void reloadLandWard() {
        reloadConfig();
        if (blockSelection != null) blockSelection.clear();
        if (blockProtectionGui != null) blockProtectionGui.closeAll();
        mergeNewConfigDefaults();
        localeService.initialize();
        landManager.memberDefaults(playerMemberDefaults(), administrativeMemberDefaults());
        applyEnvironmentDefaults();
        syncMapIntegrations();
        updateChecker.check();
        metricsService.initialize();
    }

    private MemberPermissions playerMemberDefaults() {
        return memberDefaults("player-claims.member-rights");
    }
    private void applyEnvironmentDefaults() {
        var settings = new java.util.ArrayList<io.github.devkruidenier.landward.model.EnvironmentSettings>();
        for (boolean admin : new boolean[]{false, true}) {
            var defaults = io.github.devkruidenier.landward.model.EnvironmentSettings.defaults(admin);
            for (var rule : io.github.devkruidenier.landward.model.EnvironmentSettings.Rule.values()) {
                String key = (admin ? "admin-claims" : "player-claims") + ".environment." + rule.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
                defaults = defaults.with(rule, getConfig().getBoolean(key, defaults.allows(rule)));
            }
            settings.add(defaults);
        }
        landManager.environmentDefaults(settings.get(0), settings.get(1));
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
        synchronized (configLock) { mergeNewConfigDefaultsLocked(); }
    }

    private void mergeNewConfigDefaultsLocked() {
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
            // Keep all values selected by the server owner, while writing the
            // bundled order, grouping and comments. New keys come from the JAR;
            // existing values remain untouched. No external defaults/baseline
            // file is needed beside the administrator's configuration.
            for (String path : new java.util.ArrayList<>(bundled.getKeys(true))) {
                if (!bundled.isConfigurationSection(path) && existing.contains(path)) {
                    bundled.set(path, existing.get(path));
                }
            }
            // Fresh files already contain enabled=true from saveDefaultConfig. Older files
            // without explicit opt-in must not silently enable personal protection.
            if (!existing.contains("block-protection.enabled")) bundled.set("block-protection.enabled", false);
            // This is informational metadata rather than an administrator
            // setting: always show the version of the JAR currently running.
            bundled.set("plugin-version", getDescription().getVersion());
            bundled.save(configFile);
            // Clean up the old internal baseline file from previous LandWard
            // versions. It is no longer necessary for config updates.
            Files.deleteIfExists(new File(getDataFolder(), ".config-defaults.yml").toPath());
            reloadConfig();
        } catch (Exception exception) {
            getLogger().warning("Could not merge new LandWard config defaults: " + exception.getMessage());
        }
    }

}
