package io.github.devkruidenier.landward.protection;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.admin.AdminBypassService;
import io.github.devkruidenier.landward.combat.CombatTagService;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.model.Land;
import io.github.devkruidenier.landward.model.LandType;
import io.github.devkruidenier.landward.model.MemberPermissions;
import io.github.devkruidenier.landward.model.LandProtectionSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Animals;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.Tag;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ClaimProtectionListener implements Listener {

    private final LandWardPlugin plugin;
    private final LandManager landManager;
    private final CombatTagService combatTags;
    private final AdminBypassService adminBypasses;

    public ClaimProtectionListener(LandWardPlugin plugin, LandManager landManager, CombatTagService combatTags, AdminBypassService adminBypasses) {
        this.plugin = plugin;
        this.landManager = landManager;
        this.combatTags = combatTags;
        this.adminBypasses = adminBypasses;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        denyIfNeeded(event.getPlayer(), event.getBlock().getLocation(), MemberPermissions.Permission.BUILD, event::setCancelled);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        denyIfNeeded(event.getPlayer(), event.getBlockPlaced().getLocation(), MemberPermissions.Permission.BUILD, event::setCancelled);
    }

    /** Flint & steel and fire charges ignite an air block, not a normal BlockPlaceEvent block. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockIgnite(BlockIgniteEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            denyIfNeeded(player, event.getBlock().getLocation(), MemberPermissions.Permission.BUILD, event::setCancelled);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        boolean spawningEntity = event.getItem() != null && event.getItem().getType().name().endsWith("_SPAWN_EGG");
        if (clicked != null) {
            Optional<Land> land = landAt(clicked.getLocation());
            if (land.isPresent() && landManager.typeOf(land.get()) == LandType.ADMIN) {
                if (!adminAllowedToInteract(event.getPlayer(), land.get(), clicked)) {
                    deny(event.getPlayer(), land.get(), event::setCancelled);
                }
                return;
            }
            if (land.isPresent() && landManager.typeOf(land.get()) == LandType.PLAYER) {
                if (spawningEntity) {
                    denyIfNeeded(event.getPlayer(), clicked.getLocation(), MemberPermissions.Permission.ENTITIES, event::setCancelled);
                } else if (!playerAllowedToInteract(event.getPlayer(), land.get(), clicked)) {
                    deny(event.getPlayer(), land.get(), event::setCancelled);
                }
                return;
            }
            MemberPermissions.Permission permission = spawningEntity
                    ? MemberPermissions.Permission.ENTITIES
                    : clicked.getState() instanceof InventoryHolder
                    ? MemberPermissions.Permission.CONTAINERS
                    : MemberPermissions.Permission.BUILD;
            denyIfNeeded(event.getPlayer(), clicked.getLocation(), permission, event::setCancelled);
            return;
        }
        if (spawningEntity) {
            denyIfNeeded(
                    event.getPlayer(),
                    event.getPlayer().getLocation(),
                    MemberPermissions.Permission.ENTITIES,
                    event::setCancelled
            );
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        // The clicked block can be outside a claim while the liquid is placed in its
        // neighbouring block inside one. Always protect the actual destination.
        Block destination = event.getBlockClicked().getRelative(event.getBlockFace());
        denyIfNeeded(event.getPlayer(), destination.getLocation(), MemberPermissions.Permission.BUILD, event::setCancelled);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBucketFill(PlayerBucketFillEvent event) {
        denyIfNeeded(event.getPlayer(), event.getBlockClicked().getLocation(), MemberPermissions.Permission.BUILD, event::setCancelled);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityPlace(EntityPlaceEvent event) {
        denyIfNeeded(event.getPlayer(), event.getBlock().getLocation(), MemberPermissions.Permission.ENTITIES, event::setCancelled);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHangingPlace(HangingPlaceEvent event) {
        denyIfNeeded(event.getPlayer(), event.getBlock().getLocation(), MemberPermissions.Permission.ENTITIES, event::setCancelled);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        denyIfNeeded(event.getPlayer(), event.getRightClicked().getLocation(), MemberPermissions.Permission.ENTITIES, event::setCancelled);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        denyIfNeeded(event.getPlayer(), event.getRightClicked().getLocation(), MemberPermissions.Permission.ENTITIES, event::setCancelled);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            handleEntityDamage(player, event.getEntity(), event.getEntity().getLocation(), event::setCancelled);
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            handleEntityDamage(player, event.getEntity(), event.getEntity().getLocation(), event::setCancelled);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Optional<Land> land = landAt(event.getLocation());
        if (land.isPresent() && landManager.typeOf(land.get()) == LandType.ADMIN
                && event.getEntity() instanceof Enemy
                && !plugin.getConfig().getBoolean("admin-claims.protection.allow-hostile-mob-spawns", false)) {
            event.setCancelled(true);
        }
        if (land.isPresent() && landManager.typeOf(land.get()) == LandType.PLAYER
                && event.getEntity() instanceof Enemy
                && !land.get().protectionSettings().hostileMobSpawns()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onFluidFlow(BlockFromToEvent event) {
        if (!sameProtectionDomain(event.getBlock().getLocation(), event.getToBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /** Stops fire (and similar spreading blocks) crossing a claim boundary in either direction. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (!sameProtectionDomain(event.getSource().getLocation(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /** Covers an existing fire source attempting to burn a protected block across a boundary. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockBurn(BlockBurnEvent event) {
        Block source = event.getIgnitingBlock();
        if (source != null && !sameProtectionDomain(source.getLocation(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!pistonMovementAllowed(event.getBlocks(), event.getDirection().getModX(), event.getDirection().getModY(), event.getDirection().getModZ())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!pistonMovementAllowed(event.getBlocks(), event.getDirection().getModX(), event.getDirection().getModY(), event.getDirection().getModZ())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> landAt(block.getLocation()).isPresent());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> landAt(block.getLocation()).isPresent());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (landAt(event.getBlock().getLocation()).isPresent()) {
            event.setCancelled(true);
        }
    }

    private void denyIfNeeded(
            Player player,
            Location location,
            MemberPermissions.Permission permission,
            java.util.function.Consumer<Boolean> cancellation
    ) {
        Optional<Land> land = landAt(location);
        if (land.isEmpty() || allowed(player, land.get(), permission)) {
            return;
        }

        deny(player, land.get(), cancellation);
    }

    private void deny(Player player, Land land, java.util.function.Consumer<Boolean> cancellation) {
        cancellation.accept(true);
        String locale = plugin.localeService().localeFor(player);
        String key = player.hasPermission("landward.bypass")
                && !adminBypasses.isEnabled(player.getUniqueId())
                ? "protection.denied-bypass"
                : "protection.denied";
        String message = plugin.localeService().message(locale, key, Map.of("name", land.name()));
        player.sendActionBar(Component.text(message, NamedTextColor.RED));
    }

    private boolean allowed(Player player, Land land, MemberPermissions.Permission permission) {
        if (adminBypasses.isEnabled(player.getUniqueId())) {
            return true;
        }
        MemberPermissions permissions = landManager.permissions(player.getUniqueId(), land.id());
        if (landManager.typeOf(land) == LandType.ADMIN) {
            if (!permissions.equals(MemberPermissions.NONE)) return true;
            return permission == MemberPermissions.Permission.CONTAINERS
                    && plugin.getConfig().getBoolean("admin-claims.protection.allow-containers", false);
        }
        return switch (permission) {
            case BUILD -> permissions.build();
            case CONTAINERS -> permissions.containers();
            case ENTITIES -> permissions.entities();
        };
    }

    private boolean adminDamageAllowed(Player player, org.bukkit.entity.Entity target, Location location) {
        Optional<Land> land = landAt(location);
        if (land.isEmpty() || landManager.typeOf(land.get()) != LandType.ADMIN) return false;
        if (adminBypasses.isEnabled(player.getUniqueId()) || !landManager.permissions(player.getUniqueId(), land.get().id()).equals(MemberPermissions.NONE)) return true;
        boolean allowed = target instanceof Player
                ? plugin.getConfig().getBoolean("admin-claims.protection.allow-pvp", false)
                : target instanceof Enemy
                ? plugin.getConfig().getBoolean("admin-claims.protection.allow-hostile-mob-kills", false)
                : target instanceof Animals
                ? plugin.getConfig().getBoolean("admin-claims.protection.allow-passive-mob-kills", true)
                : false;
        return allowed;
    }

    private void handleEntityDamage(Player player, org.bukkit.entity.Entity target, Location location, java.util.function.Consumer<Boolean> cancellation) {
        Optional<Land> optionalLand = landAt(location);
        if (optionalLand.isEmpty()) return;
        Land land = optionalLand.get();
        if (landManager.typeOf(land) == LandType.ADMIN) {
            if (adminDamageAllowed(player, target, location)) return;
            if (target instanceof Player victim && combatTags.mayFightInClaim(player, victim)) return;
            denyIfNeeded(player, location, MemberPermissions.Permission.ENTITIES, cancellation);
            return;
        }
        if (target instanceof Player victim) {
            if (playerLandPvpAllowed(player, victim, land)) return;
            deny(player, land, cancellation);
            return;
        }
        if (!playerLandMobDamageAllowed(player, target, land)) {
            deny(player, land, cancellation);
        }
    }

    /** PvP in a player land is deliberately limited to people trusted in that exact land. */
    private boolean playerLandPvpAllowed(Player attacker, Player victim, Land land) {
        // A land may narrow PvP rules, but it must never override the world's own
        // PvP setting (server.properties / the world's configured PvP state).
        if (!attacker.getWorld().getPVP() || !victim.getWorld().getPVP()) return false;
        if (adminBypasses.isEnabled(attacker.getUniqueId())) return true;
        if (!land.protectionSettings().memberPvp()) return false;
        return !landManager.permissions(attacker.getUniqueId(), land.id()).equals(MemberPermissions.NONE)
                && !landManager.permissions(victim.getUniqueId(), land.id()).equals(MemberPermissions.NONE);
    }

    private boolean playerLandMobDamageAllowed(Player player, org.bukkit.entity.Entity target, Land land) {
        if (adminBypasses.isEnabled(player.getUniqueId())) return true;
        if (!landManager.permissions(player.getUniqueId(), land.id()).entities()) return false;
        LandProtectionSettings settings = land.protectionSettings();
        if (target instanceof Enemy) return settings.hostileMobKills();
        if (target instanceof Animals) return settings.passiveMobKills();
        return true;
    }

    private boolean adminAllowedToInteract(Player player, Land land, Block block) {
        if (adminBypasses.isEnabled(player.getUniqueId()) || !landManager.permissions(player.getUniqueId(), land.id()).equals(MemberPermissions.NONE)) return true;
        org.bukkit.Material type = block.getType();
        if (block.getState() instanceof InventoryHolder || type == org.bukkit.Material.ENDER_CHEST) {
            return plugin.getConfig().getBoolean("admin-claims.protection.allow-containers", false);
        }
        if (type.name().endsWith("_DOOR") || type.name().endsWith("_TRAPDOOR")) {
            return plugin.getConfig().getBoolean("admin-claims.protection.allow-doors", false);
        }
        if (Tag.SIGNS.isTagged(type) || type.name().endsWith("_BUTTON") || type.name().endsWith("_PRESSURE_PLATE") || type == org.bukkit.Material.LEVER) {
            return plugin.getConfig().getBoolean("admin-claims.protection.allow-interactions", false);
        }
        return false;
    }

    private boolean playerAllowedToInteract(Player player, Land land, Block block) {
        if (adminBypasses.isEnabled(player.getUniqueId())) return true;
        MemberPermissions permissions = landManager.permissions(player.getUniqueId(), land.id());
        if (permissions.equals(MemberPermissions.NONE)) return false;
        org.bukkit.Material type = block.getType();
        if (block.getState() instanceof InventoryHolder || type == org.bukkit.Material.ENDER_CHEST) return permissions.containers();
        if (type.name().endsWith("_DOOR") || type.name().endsWith("_TRAPDOOR")) return land.protectionSettings().doors();
        if (Tag.SIGNS.isTagged(type) || type.name().endsWith("_BUTTON") || type.name().endsWith("_PRESSURE_PLATE") || type == org.bukkit.Material.LEVER) return land.protectionSettings().interactions();
        return permissions.build();
    }

    private boolean pistonMovementAllowed(java.util.List<Block> blocks, int deltaX, int deltaY, int deltaZ) {
        for (Block block : blocks) {
            Location destination = block.getLocation().add(deltaX, deltaY, deltaZ);
            if (!sameProtectionDomain(block.getLocation(), destination)) {
                return false;
            }
        }
        return true;
    }

    private boolean sameProtectionDomain(Location first, Location second) {
        UUID firstLand = landAt(first).map(Land::id).orElse(null);
        UUID secondLand = landAt(second).map(Land::id).orElse(null);
        return Objects.equals(firstLand, secondLand);
    }

    private ChunkPosition position(Location location) {
        return new ChunkPosition(location.getWorld().getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    private Optional<Land> landAt(Location location) {
        if (location.getWorld() == null || !plugin.isLandWorld(location.getWorld())) return Optional.empty();
        return landManager.landAt(position(location));
    }
}
