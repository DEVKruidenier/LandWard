package io.github.devkruidenier.landward.blockprotection;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;

/** Additional denials only. No handler ever un-cancels another protection plugin's event. */
public final class ProtectionListener implements Listener {
  private final LandWardPlugin plugin;
  private final LandManager lands;
  private final ProtectionService service;

  public ProtectionListener(LandWardPlugin plugin, LandManager lands, ProtectionService service) {
    this.plugin = plugin;
    this.lands = lands;
    this.service = service;
  }

  private boolean mechanical(Block b) {
    if (service.mechanical(ProtectionBlocks.key(b))) return true;
    return service.enabled()
        && plugin.scheduler().ownsBlock(b)
        && ProtectionBlocks.supported(b.getType())
        && ProtectionBlocks.part(b).unit().stream().anyMatch(service::mechanical);
  }

  private boolean inventoryDenied(Player player, BlockKey key) {
    var lock = lands.personalProtections().at(key);
    return service.denied(player, key)
        || service.enabled()
            && lock != null
            && !plugin.adminBypasses().isEnabled(player.getUniqueId())
            && !lands.permissions(player.getUniqueId(), lock.landId()).containers();
  }

  private boolean denied(Player p, Block b) {
    if (service.denied(p, ProtectionBlocks.key(b))) return true;
    return service.enabled()
        && ProtectionBlocks.supported(b.getType())
        && ProtectionBlocks.part(b).unit().stream().anyMatch(k -> service.denied(p, k));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void interact(PlayerInteractEvent e) {
    if (e.getClickedBlock() != null && denied(e.getPlayer(), e.getClickedBlock())) {
      e.setCancelled(true);
      service.tell(e.getPlayer(), "locked");
    }
  }

  /**
   * Iron doors cannot accept anonymous power, so permitted player clicks operate their two halves.
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void ironDoor(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND
        || e.getAction() != Action.RIGHT_CLICK_BLOCK
        || e.getClickedBlock() == null
        || e.getClickedBlock().getType() != Material.IRON_DOOR
        || !mechanical(e.getClickedBlock())) return;
    var p = e.getPlayer();
    var key = ProtectionBlocks.key(e.getClickedBlock());
    plugin
        .scheduler()
        .player(
            p,
            () -> {
              if (e.isCancelled()
                  || !service.enabled()
                  || service.denied(p, key)
                  || !p.getWorld().getUID().equals(key.world())
                  || p.getLocation()
                          .distanceSquared(
                              new Location(p.getWorld(), key.x() + .5, key.y() + .5, key.z() + .5))
                      > 64) return;
              var land = lands.landAt(key.chunk()).orElse(null);
              if (land == null
                  || !(plugin.adminBypasses().isEnabled(p.getUniqueId())
                      || land.ownerId().equals(p.getUniqueId())
                      || lands.personalMember(land.id(), p.getUniqueId())
                          && land.protectionSettings().doors())) return;
              var lock = lands.personalProtections().at(key);
              if (lock == null) return;
              plugin
                  .scheduler()
                  .atChunk(
                      p.getWorld(),
                      key.x() >> 4,
                      key.z() >> 4,
                      () -> {
                        if (e.isCancelled()
                            || !service.enabled()
                            || lands.personalProtections().get(lock.id()) == null) return false;
                        var world = Bukkit.getWorld(key.world());
                        if (world == null) return false;
                        var clicked = world.getBlockAt(key.x(), key.y(), key.z());
                        if (!(clicked.getBlockData() instanceof Door d)) return false;
                        boolean open = !d.isOpen();
                        for (var b : lock.blocks()) {
                          var block = world.getBlockAt(b.x(), b.y(), b.z());
                          if (block.getBlockData() instanceof Door data) {
                            data.setOpen(open);
                            data.setPowered(false);
                            block.setBlockData(data, false);
                          }
                        }
                        return true;
                      });
            });
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void open(InventoryOpenEvent e) {
    if (e.getPlayer() instanceof Player p
        && keys(e.getInventory()).stream().anyMatch(b -> inventoryDenied(p, b))) {
      e.setCancelled(true);
      service.tell(p, "locked");
    }
  }

  /** An already-open view must not survive a changed lock or membership. */
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void click(InventoryClickEvent e) {
    if (e.getWhoClicked() instanceof Player p
        && keys(e.getView().getTopInventory()).stream().anyMatch(b -> inventoryDenied(p, b))) {
      e.setCancelled(true);
      plugin.scheduler().player(p, p::closeInventory);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void drag(InventoryDragEvent e) {
    if (e.getWhoClicked() instanceof Player p
        && keys(e.getView().getTopInventory()).stream().anyMatch(b -> inventoryDenied(p, b)))
      e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void breakBlock(BlockBreakEvent e) {
    if (mechanical(e.getBlock()) || nearSupportedLock(e.getBlock())) {
      e.setCancelled(true);
      service.tell(e.getPlayer(), "locked");
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void place(BlockPlaceEvent e) {
    if (service.enabled()
        && (mechanical(e.getBlockPlaced()) || chestMergeLocked(e.getBlockPlaced()))) {
      e.setCancelled(true);
      service.tell(e.getPlayer(), "locked");
    }
  }

  private boolean chestMergeLocked(Block b) {
    if (b.getType() != Material.CHEST && b.getType() != Material.TRAPPED_CHEST) return false;
    // Adjacent locked chests may not be joined, including two independently locked single chests.
    for (var f : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
      var key =
          new BlockKey(
              b.getWorld().getUID(), b.getX() + f.getModX(), b.getY(), b.getZ() + f.getModZ());
      var locked = lands.personalProtections().at(key);
      if (service.uncertain(key) || locked != null && locked.material().equals(b.getType().name()))
        return true;
    }
    return false;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void placed(BlockPlaceEvent e) {
    var b = e.getBlockPlaced();
    var key = ProtectionBlocks.key(b);
    if (!ProtectionBlocks.supported(b.getType())) {
      if (lands.personalProtections().at(key) != null
          || lands.personalProtections().placement(key) != null)
        service.observe(List.of(), List.of(key), null);
      return;
    }
    if (!service.enabled() && b.getBlockData() instanceof org.bukkit.block.data.type.Chest) {
      var stale = new ArrayList<BlockKey>();
      for (var part : ProtectionBlocks.part(b).unit())
        if (lands.personalProtections().at(part) != null) stale.add(part);
      if (!stale.isEmpty()) service.observe(List.of(), stale, null);
    }
    var land = lands.landAt(key.chunk()).orElse(null);
    if (land == null || !lands.personalMember(land.id(), e.getPlayer().getUniqueId())) return;
    var positions = new ArrayList<BlockKey>();
    positions.add(key);
    if (b.getBlockData() instanceof Door door)
      positions.add(
          new BlockKey(
              key.world(),
              key.x(),
              key.y() + (door.getHalf() == Bisected.Half.TOP ? -1 : 1),
              key.z()));
    var data =
        positions.stream()
            .map(k ->
                new BlockPlacement(
                    k,
                    land.id(),
                    e.getPlayer().getUniqueId(),
                    b.getType().name(),
                    UUID.randomUUID()))
            .toList();
    var p = e.getPlayer();
    var actor = service.actor(p);
    if (service.enabled()
        && plugin.getConfig().getBoolean("block-protection.auto-protect", false)
        && actor.create())
      service.automatic(
          p, actor, land.id(), b.getType().name(), ProtectionBlocks.part(b).unit(), data);
    else service.observe(data, List.of(), null);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void broken(BlockBreakEvent e) {
    var b = e.getBlock();
    var key = ProtectionBlocks.key(b);
    var p = lands.personalProtections().at(key);
    var keys = new ArrayList<BlockKey>();
    keys.add(key);
    if (p != null) keys.addAll(p.blocks());
    if (b.getBlockData() instanceof Door d)
      keys.add(
          new BlockKey(
              key.world(),
              key.x(),
              key.y() + (d.getHalf() == Bisected.Half.TOP ? -1 : 1),
              key.z()));
    if (p != null || lands.personalProtections().placement(key) != null)
      service.observe(List.of(), keys, null);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void move(InventoryMoveItemEvent e) {
    if (blocked(e.getSource()) || blocked(e.getDestination())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void pickup(InventoryPickupItemEvent e) {
    if (blocked(e.getInventory())) e.setCancelled(true);
  }

  private boolean blocked(Inventory inventory) {
    return keys(inventory).stream().anyMatch(service::mechanical);
  }

  private List<BlockKey> keys(Inventory inventory) {
    var holder = inventory.getHolder();
    if (holder instanceof DoubleChest d) {
      var result = new ArrayList<BlockKey>();
      if (d.getLeftSide() instanceof BlockState b) result.add(ProtectionBlocks.key(b.getBlock()));
      if (d.getRightSide() instanceof BlockState b) result.add(ProtectionBlocks.key(b.getBlock()));
      return result;
    }
    if (holder instanceof BlockState b) return List.of(ProtectionBlocks.key(b.getBlock()));
    return List.of();
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void extend(BlockPistonExtendEvent e) {
    if (piston(e.getBlocks(), e.getDirection())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void retract(BlockPistonRetractEvent e) {
    if (piston(e.getBlocks(), e.getDirection())) e.setCancelled(true);
  }

  private boolean piston(List<Block> blocks, BlockFace direction) {
    return service.enabled()
        && blocks.stream()
            .anyMatch(
                b -> mechanical(b) || mechanical(b.getRelative(direction)) || nearSupportedLock(b));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void entityExplosion(EntityExplodeEvent e) {
    explode(e.blockList());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void blockExplosion(BlockExplodeEvent e) {
    explode(e.blockList());
  }

  private void explode(List<Block> blocks) {
    if (service.enabled()) blocks.removeIf(b -> mechanical(b) || nearSupportedLock(b));
    else {
      var stale =
          blocks.stream()
              .map(ProtectionBlocks::key)
              .filter(b ->
                  lands.personalProtections().at(b) != null
                      || lands.personalProtections().placement(b) != null)
              .toList();
      if (!stale.isEmpty()) service.observe(List.of(), stale, null);
    }
  }

  /**
   * Locked access blocks do not accept unattributed redstone, including pressure plates and
   * observers.
   */
  @EventHandler(priority = EventPriority.HIGHEST)
  public void redstone(BlockRedstoneEvent e) {
    if (mechanical(e.getBlock())) e.setNewCurrent(0);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void physics(BlockPhysicsEvent e) {
    if (mechanical(e.getBlock()) || nearSupportedLock(e.getBlock())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void burn(BlockBurnEvent e) {
    if (mechanical(e.getBlock()) || nearSupportedLock(e.getBlock())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void flow(BlockFromToEvent e) {
    if (mechanical(e.getToBlock()) || nearSupportedLock(e.getToBlock())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void entityChange(EntityChangeBlockEvent e) {
    if (mechanical(e.getBlock()) || nearSupportedLock(e.getBlock())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void dispense(BlockDispenseEvent e) {
    // Limit conservative automation denial to a dispenser aimed at a locked unit/support.
    var data = e.getBlock().getBlockData();
    if (data instanceof org.bukkit.block.data.Directional d) {
      var target = e.getBlock().getRelative(d.getFacing());
      if (mechanical(target) || nearSupportedLock(target)) e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void bucket(PlayerBucketEmptyEvent e) {
    var target = e.getBlockClicked().getRelative(e.getBlockFace());
    if (mechanical(target) || nearSupportedLock(target)) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void burnt(BlockBurnEvent e) {
    invalidateDestroyed(e.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void changed(EntityChangeBlockEvent e) {
    invalidateDestroyed(e.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void moved(BlockPistonExtendEvent e) {
    if (!service.enabled()) e.getBlocks().forEach(this::invalidateDestroyed);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void pulled(BlockPistonRetractEvent e) {
    if (!service.enabled()) e.getBlocks().forEach(this::invalidateDestroyed);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void fade(BlockFadeEvent e) {
    if (mechanical(e.getBlock()) || nearSupportedLock(e.getBlock())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void form(BlockFormEvent e) {
    if (mechanical(e.getBlock()) || nearSupportedLock(e.getBlock())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void flowed(BlockFromToEvent e) {
    revalidateLater(e.getToBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void faded(BlockFadeEvent e) {
    revalidateLater(e.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void formed(BlockFormEvent e) {
    revalidateLater(e.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void chunkLoaded(org.bukkit.event.world.ChunkLoadEvent e) {
    // Examine only known positions in this chunk, never a scan of world blocks.
    var world = e.getWorld().getUID();
    int x = e.getChunk().getX(), z = e.getChunk().getZ();
    for (var b :
        lands
            .personalProtections()
            .inChunk(new io.github.devkruidenier.landward.model.ChunkPosition(world, x, z)))
      revalidateLater(e.getWorld().getBlockAt(b.x(), b.y(), b.z()));
  }

  private void revalidateLater(Block block) {
    var key = ProtectionBlocks.key(block);
    var lock = lands.personalProtections().at(key);
    var placed = lands.personalProtections().placement(key);
    if (lock == null && placed == null) return;
    String material = lock != null ? lock.material() : placed.material();
    plugin
        .scheduler()
        .atChunk(
            block.getWorld(),
            key.x() >> 4,
            key.z() >> 4,
            () -> {
              if (!block.getType().name().equals(material)
                  || lock != null
                      && ProtectionBlocks.supported(block.getType())
                      && !Set.copyOf(ProtectionBlocks.part(block).unit())
                          .equals(Set.copyOf(lock.blocks())))
                service.observe(List.of(), List.of(key), null);
              return true;
            });
  }

  private void invalidateDestroyed(Block b) {
    var k = ProtectionBlocks.key(b);
    if (lands.personalProtections().at(k) != null
        || lands.personalProtections().placement(k) != null)
      service.observe(List.of(), List.of(k), null);
  }

  private boolean nearSupportedLock(Block support) {
    if (!service.enabled()) return false;
    // Removing the block beneath a door can destroy both halves without a BlockBreakEvent for the
    // door.
    var above =
        new BlockKey(
            support.getWorld().getUID(), support.getX(), support.getY() + 1, support.getZ());
    var p = lands.personalProtections().at(above);
    return p != null && p.material().endsWith("_DOOR");
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void spread(BlockSpreadEvent e) {
    if (mechanical(e.getBlock()) || nearSupportedLock(e.getBlock())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void structure(org.bukkit.event.world.StructureGrowEvent e) {
    if (service.enabled()
        && e.getBlocks().stream()
            .anyMatch(b -> mechanical(b.getBlock()) || nearSupportedLock(b.getBlock())))
      e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void fertilize(BlockFertilizeEvent e) {
    if (service.enabled()
        && e.getBlocks().stream()
            .anyMatch(b -> mechanical(b.getBlock()) || nearSupportedLock(b.getBlock())))
      e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void spreadObserved(BlockSpreadEvent e) {
    revalidateLater(e.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void structureObserved(org.bukkit.event.world.StructureGrowEvent e) {
    e.getBlocks().forEach(b -> revalidateLater(b.getBlock()));
  }
}
