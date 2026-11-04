package io.github.devkruidenier.landward.protection;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.model.Land;
import io.github.devkruidenier.landward.model.MemberPermissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.Optional;

public final class ClaimProtectionListener implements Listener {

    private final LandWardPlugin plugin;
    private final LandManager landManager;

    public ClaimProtectionListener(LandWardPlugin plugin, LandManager landManager) {
        this.plugin = plugin;
        this.landManager = landManager;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        denyIfNeeded(event.getPlayer(), event.getBlock().getLocation(), MemberPermissions.Permission.BUILD, event::setCancelled);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        denyIfNeeded(event.getPlayer(), event.getBlockPlaced().getLocation(), MemberPermissions.Permission.BUILD, event::setCancelled);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked != null) {
            MemberPermissions.Permission permission = clicked.getState() instanceof InventoryHolder
                    ? MemberPermissions.Permission.CONTAINERS
                    : MemberPermissions.Permission.BUILD;
            denyIfNeeded(event.getPlayer(), clicked.getLocation(), permission, event::setCancelled);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        denyIfNeeded(event.getPlayer(), event.getBlockClicked().getLocation(), MemberPermissions.Permission.BUILD, event::setCancelled);
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

    private void denyIfNeeded(
            Player player,
            Location location,
            MemberPermissions.Permission permission,
            java.util.function.Consumer<Boolean> cancellation
    ) {
        Optional<Land> land = landManager.landAt(position(location));
        if (land.isEmpty() || allowed(player, land.get(), permission)) {
            return;
        }

        cancellation.accept(true);
        String locale = plugin.localeService().localeFor(player);
        String message = plugin.localeService().message(locale, "protection.denied", Map.of("name", land.get().name()));
        player.sendActionBar(Component.text(message, NamedTextColor.RED));
    }

    private boolean allowed(Player player, Land land, MemberPermissions.Permission permission) {
        if (player.hasPermission("landward.bypass")) {
            return true;
        }
        MemberPermissions permissions = landManager.permissions(player.getUniqueId(), land.id());
        return switch (permission) {
            case BUILD -> permissions.build();
            case CONTAINERS -> permissions.containers();
            case ENTITIES -> permissions.entities();
        };
    }

    private ChunkPosition position(Location location) {
        return new ChunkPosition(location.getWorld().getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }
}
