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
  private final Map<BlockKey, DoorBreak> doorBreaks =
      new java.util.concurrent.ConcurrentHashMap<>();

  private record DoorBreak(BlockBreakEvent event, BlockProtection lock) {}

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
            && (lock != null || service.automation(key).owner() != null)
            && !plugin.adminBypasses().isEnabled(player.getUniqueId())
            && !lands
                .landAt(key.chunk())
                .map(l -> lands.permissions(player.getUniqueId(), l.id()).containers())
                .orElse(true);
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

  /** Update a protected door as one operation on its owning region. */
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void protectedDoor(PlayerInteractEvent e) {
    var clicked = e.getClickedBlock();
    if (e.getHand() != EquipmentSlot.HAND
        || e.getAction() != Action.RIGHT_CLICK_BLOCK
        || clicked == null
        || !plugin.scheduler().ownsBlock(clicked)
        || clicked.getType() == Material.IRON_DOOR
        || !(clicked.getBlockData() instanceof Door door)
        || !service.enabled()) return;

    var key = ProtectionBlocks.key(clicked);
    var lock = lands.personalProtections().at(key);
    if (lock == null || !lock.material().equals(clicked.getType().name())) return;
    var player = e.getPlayer();
    if (player.isSneaking()
        && (e.hasItem() || !player.getInventory().getItemInOffHand().getType().isAir())
        && e.useInteractedBlock() != Event.Result.ALLOW) return;

    if (!canUseDoor(player, lock)) return;
    if (setDoor(clicked, door, lock, !door.isOpen(), player)) {
      e.setUseInteractedBlock(Event.Result.DENY);
      e.setUseItemInHand(Event.Result.DENY);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void protectedLever(PlayerInteractEvent event) {
    var lever = event.getClickedBlock();
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK
        || lever == null
        || !service.enabled()
        || !plugin.scheduler().ownsBlock(lever)
        || lever.getType() != Material.LEVER
        || !(lever.getBlockData() instanceof org.bukkit.block.data.type.Switch before)) return;
    var key = ProtectionBlocks.key(lever);
    var lock = lands.personalProtections().at(key);
    var player = event.getPlayer();
    if (lock == null || !lock.material().equals("LEVER") || service.denied(player, key)) return;

    // Vanilla toggles the lever after this event. Check the actual outcome before using it.
    plugin
        .scheduler()
        .player(
            player,
            () -> {
              if (event.isCancelled()
                  || !service.enabled()
                  || !plugin.scheduler().ownsBlock(lever)
                  || lever.getType() != Material.LEVER
                  || !lock.equals(lands.personalProtections().at(key))
                  || service.denied(player, key)
                  || !(lever.getBlockData() instanceof org.bukkit.block.data.type.Switch current)
                  || current.isPowered() == before.isPowered()
                  || current.getAttachedFace() != before.getAttachedFace()
                  || current.getFacing() != before.getFacing()) return;
              var backing = lever.getRelative(supportFace(current));
              var targets = new LinkedHashSet<Block>();
              for (var face : DIRECT_NEIGHBORS) targets.add(lever.getRelative(face));
              if (plugin.scheduler().ownsBlock(backing) && backing.getType().isOccluding()) {
                for (var face : DIRECT_NEIGHBORS) targets.add(backing.getRelative(face));
              }
              for (var target : targets) {
                if (!plugin.scheduler().ownsBlock(target)) continue;
                var doorLock = lands.personalProtections().at(ProtectionBlocks.key(target));
                if (doorLock != null
                    && doorLock.owner().equals(lock.owner())
                    && doorLock.landId().equals(lock.landId())
                    && canUseDoor(player, doorLock)
                    && target.getBlockData() instanceof Door door
                    && doorLock.material().equals(target.getType().name())) {
                  setDoor(target, door, doorLock, current.isPowered(), player);
                }
              }
            });
  }

  private static final List<BlockFace> DIRECT_NEIGHBORS =
      List.of(
          BlockFace.UP,
          BlockFace.DOWN,
          BlockFace.NORTH,
          BlockFace.SOUTH,
          BlockFace.EAST,
          BlockFace.WEST);

  private static BlockFace supportFace(org.bukkit.block.data.type.Switch control) {
    return switch (control.getAttachedFace()) {
      case FLOOR -> BlockFace.DOWN;
      case CEILING -> BlockFace.UP;
      case WALL -> control.getFacing().getOppositeFace();
    };
  }

  private boolean canUseDoor(Player player, BlockProtection lock) {
    if (lock.blocks().stream().anyMatch(b -> service.denied(player, b))) return false;
    var land = lands.landAt(lock.blocks().getFirst().chunk()).orElse(null);
    return land != null
        && land.id().equals(lock.landId())
        && (plugin.adminBypasses().isEnabled(player.getUniqueId())
            || land.ownerId().equals(player.getUniqueId())
            || lands.personalMember(land.id(), player.getUniqueId())
                && land.protectionSettings().doors());
  }

  private boolean setDoor(
      Block clicked, Door door, BlockProtection lock, boolean open, Player player) {
    var partner =
        clicked.getRelative(door.getHalf() == Bisected.Half.TOP ? BlockFace.DOWN : BlockFace.UP);
    if (!lock.blocks().contains(ProtectionBlocks.key(partner))
        || partner.getType() != clicked.getType()
        || !(partner.getBlockData() instanceof Door otherHalf)
        || otherHalf.getHalf() == door.getHalf()
        || otherHalf.getFacing() != door.getFacing()
        || otherHalf.getHinge() != door.getHinge()) return false;
    if (door.isOpen() == open && otherHalf.isOpen() == open) return true;
    door.setOpen(open);
    otherHalf.setOpen(open);
    door.setPowered(false);
    otherHalf.setPowered(false);
    clicked.setBlockData(door, false);
    partner.setBlockData(otherHalf, false);
    var world = clicked.getWorld();
    var location = clicked.getLocation().add(.5, .5, .5);
    world.playSound(location, doorSound(clicked.getType(), open), SoundCategory.BLOCKS, 1, 1);
    if (CAN_SEND_GAME_EVENT) {
      world.sendGameEvent(
          Bukkit.getPlayer(player.getUniqueId()),
          open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE,
          location.toVector());
    }
    return true;
  }

  private static final boolean CAN_SEND_GAME_EVENT = canSendGameEvent();

  private static boolean canSendGameEvent() {
    try {
      World.class.getMethod(
          "sendGameEvent",
          org.bukkit.entity.Entity.class,
          GameEvent.class,
          org.bukkit.util.Vector.class);
      return true;
    } catch (NoSuchMethodException ignored) {
      return false;
    }
  }

  private static String doorSound(Material material, boolean open) {
    String family =
        switch (material) {
          case IRON_DOOR -> "iron";
          case BAMBOO_DOOR -> "bamboo_wood";
          case CHERRY_DOOR -> "cherry_wood";
          case CRIMSON_DOOR, WARPED_DOOR -> "nether_wood";
          default -> material.name().endsWith("COPPER_DOOR") ? "copper" : "wooden";
        };
    return "minecraft:block." + family + "_door." + (open ? "open" : "close");
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
    var key = ProtectionBlocks.key(e.getBlock());
    var network =
        e.getBlock().getType() == Material.HOPPER
            ? service.automation(key)
            : new ProtectionService.AutomationState(null, false);
    boolean ownHopper =
        network.owner() != null
            && !service.uncertain(key)
            && (network.owner().equals(e.getPlayer().getUniqueId())
                || e.getPlayer().hasPermission("landward.admin.protection.bypass"));
    var lock = lands.personalProtections().at(key);
    boolean ownBlock = canBreakOwnLock(e, lock);
    if (mechanical(e.getBlock()) && !ownHopper && !ownBlock && !ordinaryPairedBlock(e.getBlock())
        || nearSupportedLock(e.getBlock())) {
      e.setCancelled(true);
      service.tell(e.getPlayer(), "locked");
    }
  }

  private boolean canBreakOwnLock(BlockBreakEvent event, BlockProtection lock) {
    if (lock == null || !lock.material().equals(event.getBlock().getType().name())) return false;
    var player = event.getPlayer();
    return ProtectionBlocks.supported(event.getBlock().getType())
        && lock.owner().equals(player.getUniqueId())
        && service.actor(player).own()
        && lands.personalMember(lock.landId(), player.getUniqueId())
        && lock.blocks().stream().noneMatch(service::uncertain);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void place(BlockPlaceEvent e) {
    if (service.enabled()
        && mechanical(e.getBlockPlaced())
        && !ordinaryPairedBlock(e.getBlockPlaced())
        && !canExtend(e.getPlayer(), e.getBlockPlaced(), mergedLock(e.getBlockPlaced()))) {
      e.setCancelled(true);
      service.tell(e.getPlayer(), "locked");
    }
  }

  private BlockProtection mergedLock(Block b) {
    if (!(b.getBlockData() instanceof org.bukkit.block.data.type.Chest)) return null;
    var key = ProtectionBlocks.key(b);
    return ProtectionBlocks.part(b).unit().stream()
        .filter(k -> !k.equals(key))
        .map(lands.personalProtections()::at)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private boolean canExtend(Player player, Block b, BlockProtection lock) {
    if (lock == null
        || lock.blocks().size() != 1
        || !lock.owner().equals(player.getUniqueId())
        || !lock.material().equals(b.getType().name())
        || !service.actor(player).create()
        || !service.actor(player).own()
        || !lands.personalMember(lock.landId(), player.getUniqueId())) return false;
    var unit = ProtectionBlocks.part(b).unit();
    return unit.size() == 2
        && lands.personalProtections().at(ProtectionBlocks.key(b)) == null
        && unit.stream().noneMatch(service::uncertain)
        && unit.stream()
            .allMatch(
                k -> lands.landAt(k.chunk()).map(l -> l.id().equals(lock.landId())).orElse(false));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void placed(BlockPlaceEvent e) {
    var b = e.getBlockPlaced();
    var key = ProtectionBlocks.key(b);
    var merged = service.enabled() ? mergedLock(b) : null;
    if (canExtend(e.getPlayer(), b, merged)) {
      var shape = (org.bukkit.block.data.type.Chest) b.getBlockData();
      service.expandChest(
          service.actor(e.getPlayer()),
          merged,
          new BlockPlacement(
              key,
              merged.landId(),
              e.getPlayer().getUniqueId(),
              b.getType().name(),
              UUID.randomUUID()),
          shape);
      return;
    }
    if (!ProtectionBlocks.supported(b.getType()) && b.getType() != Material.HOPPER) {
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
    if (ProtectionBlocks.supported(b.getType())
        && service.enabled()
        && plugin.getConfig().getBoolean("block-protection.auto-protect", false)
        && actor.create())
      service.automatic(
          p, actor, land.id(), b.getType().name(), ProtectionBlocks.part(b).unit(), data);
    else if (isPairedBlock(b)) service.observePairedBlock(data, List.of());
    else service.observe(data, List.of(), null);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void broken(BlockBreakEvent e) {
    var b = e.getBlock();
    var key = ProtectionBlocks.key(b);
    var p = lands.personalProtections().at(key);
    if (service.enabled() && p != null && b.getBlockData() instanceof Door) {
      if (canBreakOwnLock(e, p)) completeDoorBreak(e, p);
      return;
    }
    if (service.enabled()
        && p != null
        && p.storage()
        && p.owner().equals(e.getPlayer().getUniqueId())) {
      service.breakStorage(service.actor(e.getPlayer()), p, key);
      return;
    }
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
    if (p != null
        || keys.stream()
            .anyMatch(
                k -> lands.personalProtections().placement(k) != null || service.metadataOnly(k))) {
      if (p == null && isPairedBlock(b)) service.observePairedBlock(List.of(), keys);
      else service.observe(List.of(), keys, null);
    }
  }

  private void completeDoorBreak(BlockBreakEvent event, BlockProtection lock) {
    var block = event.getBlock();
    var key = ProtectionBlocks.key(block);
    var unit = Set.copyOf(ProtectionBlocks.part(block).unit());
    if (!unit.equals(Set.copyOf(lock.blocks()))) return;

    // Keep the lock until vanilla has actually removed the door, but let this one approved
    // break propagate to the other half so vanilla handles the drops.
    var approved = new DoorBreak(event, lock);
    unit.forEach(part -> doorBreaks.put(part, approved));
    plugin
        .scheduler()
        .atChunk(
            block.getWorld(),
            key.x() >> 4,
            key.z() >> 4,
            () -> {
              if (event.isCancelled()
                  || block.getType().name().equals(lock.material())
                  || lands.personalProtections().get(lock.id()) == null) return false;
              for (var part : unit) {
                if (part.equals(key)) continue;
                var other = block.getWorld().getBlockAt(part.x(), part.y(), part.z());
                if (other.getType().name().equals(lock.material())
                    && Set.copyOf(ProtectionBlocks.part(other).unit()).equals(unit)) {
                  // Some servers suppress the neighbour update. This half must not drop a second
                  // item.
                  other.setType(Material.AIR, false);
                }
              }
              service.observe(List.of(), lock.blocks(), null);
              return true;
            })
        .whenComplete((result, error) -> unit.forEach(part -> doorBreaks.remove(part, approved)));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void move(InventoryMoveItemEvent e) {
    if (!service.enabled()) return;
    var source = owners(e.getSource());
    var target = owners(e.getDestination());
    if (source == null || target == null) {
      e.setCancelled(true);
      return;
    }
    var protectedOwners = new HashSet<>(source);
    protectedOwners.addAll(target);
    if (protectedOwners.isEmpty()) return;
    if (protectedOwners.size() != 1) {
      e.setCancelled(true);
      return;
    }
    var owner = protectedOwners.iterator().next();
    var initiator = hopperOwner(e.getInitiator());
    if (!owner.equals(initiator)
        || !source.isEmpty() && !target.contains(owner)
        || java.util.stream.Stream.concat(
                keys(e.getSource()).stream(), keys(e.getDestination()).stream())
            .anyMatch(b -> !service.automationRights(owner, b))) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void pickup(InventoryPickupItemEvent e) {
    if (!service.enabled()) return;
    var owner = hopperOwner(e.getInventory());
    var scope = owners(e.getInventory());
    if (scope == null
        || scope != null && !scope.isEmpty() && (owner == null || !scope.contains(owner)))
      e.setCancelled(true);
  }

  private Set<UUID> owners(Inventory inventory) {
    var result = new HashSet<UUID>();
    for (var b : keys(inventory)) {
      if (service.uncertain(b)) return null;
      var p = lands.personalProtections().at(b);
      if (p != null) result.add(p.owner());
      else {
        var network = service.automation(b);
        if (network.uncertain()) return null;
        if (network.owner() != null) {
          for (var id : service.automationOrigins(b)) {
            var origin = lands.personalProtections().get(id);
            if (origin == null
                || !service.automationRights(network.owner(), origin.blocks().getFirst()))
              return null;
          }
          result.add(network.owner());
        }
      }
    }
    return result;
  }

  private UUID hopperOwner(Inventory inventory) {
    if (!(inventory.getHolder() instanceof Hopper)) return null;
    var positions = keys(inventory);
    if (positions.size() != 1) return null;
    var p = lands.personalProtections().placement(positions.getFirst());
    return p != null
            && p.material().equals("HOPPER")
            && service.automationRights(p.owner(), p.position())
        ? p.owner()
        : null;
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
    if (mechanical(e.getBlock())
        && e.getBlock().getBlockData() instanceof org.bukkit.block.data.Openable)
      e.setNewCurrent(0);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void physics(BlockPhysicsEvent e) {
    var block = e.getBlock();
    var key = ProtectionBlocks.key(block);
    var doorBreak = doorBreaks.get(key);
    var lock = lands.personalProtections().at(key);
    boolean approvedDoorRemoval =
        doorBreak != null
            && !doorBreak.event().isCancelled()
            && lock != null
            && doorBreak.lock().id().equals(lock.id());
    boolean ordinaryRemoval =
        block.getType().isAir()
            && service.metadataOnly(key)
            && lands.personalProtections().at(key) == null;
    if ((mechanical(block)
            && block.getType() != Material.HOPPER
            && !approvedDoorRemoval
            && !ordinaryRemoval
            && !ordinaryPairedBlock(block))
        || nearSupportedLock(block)) e.setCancelled(true);
  }

  private static boolean isPairedBlock(Block block) {
    var data = block.getBlockData();
    return data instanceof Door || data instanceof org.bukkit.block.data.type.Chest;
  }

  private boolean ordinaryPairedBlock(Block block) {
    return plugin.scheduler().ownsBlock(block)
        && isPairedBlock(block)
        && ProtectionBlocks.part(block).unit().stream()
            .allMatch(key ->
                lands.personalProtections().at(key) == null
                    && (!service.uncertain(key) || service.metadataOnly(key)));
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
  public void entityInteract(EntityInteractEvent e) {
    // Wooden buttons must not be activated by an unattributed arrow or other entity.
    if (mechanical(e.getBlock())) e.setCancelled(true);
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
    if (p != null
        && p.material().endsWith("_DOOR")
        && !p.blocks().contains(ProtectionBlocks.key(support))) return true;
    for (var face :
        List.of(
            BlockFace.UP,
            BlockFace.DOWN,
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST)) {
      var key =
          new BlockKey(
              support.getWorld().getUID(),
              support.getX() + face.getModX(),
              support.getY() + face.getModY(),
              support.getZ() + face.getModZ());
      var control = lands.personalProtections().at(key);
      if (control == null
          || !(control.material().equals("LEVER") || control.material().endsWith("_BUTTON")))
        continue;
      var block = support.getWorld().getBlockAt(key.x(), key.y(), key.z());
      if (!plugin.scheduler().ownsBlock(block)) return true;
      if (!(block.getBlockData() instanceof org.bukkit.block.data.type.Switch data)) continue;
      var direction =
          switch (data.getAttachedFace()) {
            case FLOOR -> BlockFace.DOWN;
            case CEILING -> BlockFace.UP;
            case WALL -> data.getFacing().getOppositeFace();
          };
      if (ProtectionBlocks.key(block.getRelative(direction)).equals(ProtectionBlocks.key(support)))
        return true;
    }
    return false;
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
