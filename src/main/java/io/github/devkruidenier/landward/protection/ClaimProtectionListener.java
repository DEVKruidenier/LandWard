package io.github.devkruidenier.landward.protection;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.model.Land;
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
        denyIfNeeded(event.getPlayer(), event.getBlock().getLocation(), event::setCancelled);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        denyIfNeeded(event.getPlayer(), event.getBlockPlaced().getLocation(), event::setCancelled);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked != null) {
            denyIfNeeded(event.getPlayer(), clicked.getLocation(), event::setCancelled);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        denyIfNeeded(event.getPlayer(), event.getBlockClicked().getLocation(), event::setCancelled);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBucketFill(PlayerBucketFillEvent event) {
        denyIfNeeded(event.getPlayer(), event.getBlockClicked().getLocation(), event::setCancelled);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityPlace(EntityPlaceEvent event) {
        denyIfNeeded(event.getPlayer(), event.getBlock().getLocation(), event::setCancelled);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHangingPlace(HangingPlaceEvent event) {
        denyIfNeeded(event.getPlayer(), event.getBlock().getLocation(), event::setCancelled);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        denyIfNeeded(event.getPlayer(), event.getRightClicked().getLocation(), event::setCancelled);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        denyIfNeeded(event.getPlayer(), event.getRightClicked().getLocation(), event::setCancelled);
    }

    private void denyIfNeeded(Player player, Location location, java.util.function.Consumer<Boolean> cancellation) {
        Optional<Land> land = landManager.landAt(position(location));
        if (land.isEmpty() || allowed(player, land.get())) {
            return;
        }

        cancellation.accept(true);
        String locale = plugin.localeService().localeFor(player);
        String message = plugin.localeService().message(locale, "protection.denied", Map.of("name", land.get().name()));
        player.sendActionBar(Component.text(message, NamedTextColor.RED));
    }

    private boolean allowed(Player player, Land land) {
        return player.hasPermission("landward.bypass") || land.ownerId().equals(player.getUniqueId());
    }

    private ChunkPosition position(Location location) {
        return new ChunkPosition(location.getWorld().getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }
}
