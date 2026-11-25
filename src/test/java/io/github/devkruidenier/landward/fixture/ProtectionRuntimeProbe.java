package io.github.devkruidenier.landward.fixture;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.blockprotection.*;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.storage.sqlite.SqliteLandStorage;
import java.lang.reflect.Proxy;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;

/**
 * Real world blocks, Bukkit inventories and event objects; synthetic players, not interactive
 * acceptance.
 */
public final class ProtectionRuntimeProbe {
  private int checks;
  private Inventory top;
  private boolean admin;
  private boolean sneaking;

  public static int run(LandWardPlugin plugin) throws Exception {
    return new ProtectionRuntimeProbe().verify(plugin);
  }

  private int verify(LandWardPlugin plugin) throws Exception {
    var world = Bukkit.getWorlds().getFirst();
    int x = 24004, y = 100, z = 24004;
    var blocks = new LinkedHashMap<Block, org.bukkit.block.data.BlockData>();
    for (int dx = -1; dx < 9; dx++)
      for (int dy = -1; dy < 3; dy++) {
        var b = world.getBlockAt(x + dx, y + dy, z);
        blocks.put(b, b.getBlockData());
        b.setType(Material.AIR, false);
      }
    var paths = List.of("block-protection.enabled", "block-protection.land-owner-bypass");
    var old = new HashMap<String, Object>();
    paths.forEach(k -> old.put(k, plugin.getConfig().get(k)));
    var directory = plugin.getDataFolder().toPath().resolve("probe");
    java.nio.file.Files.createDirectories(directory);
    UUID owner = UUID.randomUUID(), member = UUID.randomUUID(), guest = UUID.randomUUID();
    try (var db = new SqliteLandStorage(directory.resolve("locks-" + UUID.randomUUID() + ".db"))) {
      db.initialize();
      var lands = new LandManager(db);
      lands.load();
      var land =
          lands
              .create(owner, "Lock Probe", new ChunkPosition(world.getUID(), x >> 4, z >> 4))
              .land();
      lands.addMember(owner, member);
      plugin.getConfig().set("block-protection.enabled", true);
      plugin.getConfig().set("block-protection.land-owner-bypass", false);
      try (var service = new ProtectionService(plugin, lands)) {
        var listener = new ProtectionListener(plugin, lands, service);
        var gui = new ProtectionGui(plugin, lands, service);
        Player lockOwner = player(member, world, x, y, z),
            landOwner = player(owner, world, x, y, z),
            visitor = player(guest, world, x, y, z);
        var chest = world.getBlockAt(x, y, z);
        chest.setType(Material.CHEST, false);
        var hopper = world.getBlockAt(x + 3, y, z);
        hopper.setType(Material.HOPPER, false);
        var key = ProtectionBlocks.key(chest);
        var placed = new BlockPlacement(key, land.id(), member, "CHEST", UUID.randomUUID());
        lands.recordPlacement(placed);
        var lock =
            lands.protect(
                new ProtectionActor(member, true, true, true, false),
                land.id(),
                "CHEST",
                List.of(key),
                Map.of(key, placed.generation()),
                -1);
        for (Player p : List.of(lockOwner, landOwner, visitor)) {
          var e = new PlayerInteractEvent(p, Action.RIGHT_CLICK_BLOCK, null, chest, BlockFace.UP);
          listener.interact(e);
          check(
              e.isCancelled() == !p.getUniqueId().equals(member),
              "Private interaction role " + p.getUniqueId());
        }
        var deniedByOther =
            new PlayerInteractEvent(lockOwner, Action.RIGHT_CLICK_BLOCK, null, chest, BlockFace.UP);
        deniedByOther.setCancelled(true);
        listener.interact(deniedByOther);
        check(deniedByOther.isCancelled(), "Other-plugin cancellation preserved");
        var chestInventory = ((org.bukkit.block.Chest) chest.getState()).getBlockInventory();
        var hopperInventory = ((Hopper) hopper.getState()).getInventory();
        var move =
            new InventoryMoveItemEvent(
                chestInventory, new ItemStack(Material.DIAMOND), hopperInventory, true);
        listener.move(move);
        check(move.isCancelled(), "Hopper extraction denied");
        move =
            new InventoryMoveItemEvent(
                hopperInventory, new ItemStack(Material.DIAMOND), chestInventory, true);
        listener.move(move);
        check(move.isCancelled(), "Hopper insertion denied");
        move =
            new InventoryMoveItemEvent(
                hopperInventory,
                new ItemStack(Material.DIAMOND),
                Bukkit.createInventory(null, 9),
                true);
        listener.move(move);
        check(!move.isCancelled(), "Unrelated hopper remains usable");
        for (var viewer : List.of(lockOwner, landOwner, visitor)) {
          var view =
              (InventoryView)
                  Proxy.newProxyInstance(
                      InventoryView.class.getClassLoader(),
                      new Class<?>[] {InventoryView.class},
                      (o, m, a) ->
                          switch (m.getName()) {
                            case "getTopInventory" -> chestInventory;
                            case "getBottomInventory" -> Bukkit.createInventory(null, 36);
                            case "getPlayer" -> viewer;
                            case "getType" -> InventoryType.CHEST;
                            default -> null;
                          });
          var open = new InventoryOpenEvent(view);
          listener.open(open);
          check(
              open.isCancelled() == !viewer.getUniqueId().equals(member),
              "InventoryOpen enforces private access");
        }
        var adjacent = world.getBlockAt(x + 1, y, z);
        var replaced = adjacent.getState();
        adjacent.setType(Material.CHEST, false);
        var merge =
            new BlockPlaceEvent(
                adjacent, replaced, chest, new ItemStack(Material.CHEST), lockOwner, true);
        listener.place(merge);
        check(merge.isCancelled(), "Joining a protected chest is denied");
        adjacent.setType(Material.AIR, false);
        var breakEvent = new BlockBreakEvent(chest, lockOwner);
        listener.breakBlock(breakEvent);
        check(breakEvent.isCancelled(), "Even owner must remove lock before breaking");
        var piston = new BlockPistonExtendEvent(hopper, List.of(chest), BlockFace.EAST);
        listener.extend(piston);
        check(piston.isCancelled(), "Piston cannot move protected unit");
        var explosionBlocks = new ArrayList<>(List.of(chest, hopper));
        var explosion =
            new EntityExplodeEvent(
                entity(world), chest.getLocation(), explosionBlocks, 1, ExplosionResult.DESTROY);
        listener.entityExplosion(explosion);
        check(
            !explosion.blockList().contains(chest) && explosion.blockList().contains(hopper),
            "Explosion filters only protected blocks");
        var burn = new BlockBurnEvent(chest);
        listener.burn(burn);
        check(burn.isCancelled(), "Fire cannot destroy lock");
        var flow = new BlockFromToEvent(hopper, chest);
        listener.flow(flow);
        check(flow.isCancelled(), "Fluids cannot replace lock");
        var change =
            new EntityChangeBlockEvent(entity(world), chest, Material.AIR.createBlockData());
        listener.entityChange(change);
        check(change.isCancelled(), "Entity changes cannot replace lock");
        gui.main(lockOwner, land.id());
        checkIcons(Material.IRON_DOOR, Material.CHEST);
        check(
            Arrays.stream(top.getContents()).noneMatch(i -> i.getType() == Material.COMMAND_BLOCK),
            "Ordinary member has no all-locks button");
        gui.main(landOwner, land.id());
        checkIcons(Material.IRON_DOOR, Material.CHEST, Material.COMMAND_BLOCK);
        gui.openLock(lockOwner, lock);
        check(
            top.getItem(4).getType() == Material.CHEST,
            "Actual block informational icon at top center");
        checkIcons(Material.IRON_BARS, Material.PLAYER_HEAD, Material.HOPPER);
        gui.openLock(landOwner, lock);
        checkIcons(Material.HOPPER);
        check(
            Arrays.stream(top.getContents()).noneMatch(i -> i.getType() == Material.IRON_BARS),
            "Land owner cannot silently rewrite member sharing");
        gui.confirm(lockOwner, "remove-warning", () -> {}, () -> {});
        check(
            top.getItem(12).getType() == Material.LIME_CONCRETE
                && top.getItem(14).getType() == Material.RED_CONCRETE,
            "Final confirmation icons");
        for (Material type :
            List.of(
                Material.CHEST,
                Material.TRAPPED_CHEST,
                Material.BARREL,
                Material.SHULKER_BOX,
                Material.WHITE_SHULKER_BOX,
                Material.OAK_DOOR,
                Material.IRON_DOOR,
                Material.OAK_TRAPDOOR,
                Material.OAK_FENCE_GATE))
          check(ProtectionBlocks.supported(type), "Material classification " + type);
        check(
            !ProtectionBlocks.supported(Material.ENDER_CHEST)
                && !ProtectionBlocks.supported(Material.HOPPER),
            "No unsupported storage categories");
        lands.removePersonal(
            new ProtectionActor(member, true, true, true, false), lock.id(), lock.revision());
        // Real double-chest block data in both orientations, checked from either half.
        var second = world.getBlockAt(x + 1, y, z);
        second.setType(Material.CHEST, false);
        for (BlockFace facing :
            List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
          Chest a = (Chest) Material.CHEST.createBlockData();
          a.setFacing(facing);
          a.setType(Chest.Type.LEFT);
          chest.setBlockData(a, false);
          var part = ProtectionBlocks.part(chest);
          var otherKey = part.unit().get(1);
          var other = world.getBlockAt(otherKey.x(), otherKey.y(), otherKey.z());
          blocks.putIfAbsent(other, other.getBlockData());
          Chest b = (Chest) Material.CHEST.createBlockData();
          b.setFacing(facing);
          b.setType(Chest.Type.RIGHT);
          other.setBlockData(b, false);
          check(
              Set.copyOf(ProtectionBlocks.part(other).unit()).equals(Set.copyOf(part.unit())),
              "Double chest partner symmetry " + facing);
          check(
              ((org.bukkit.block.Chest) chest.getState()).getInventory().getSize() == 54,
              "Native double chest inventory pairing " + facing);
        }
        lands.forgetPersonal(List.of(key));
        chest.setType(Material.IRON_DOOR, false);
        var lower = (org.bukkit.block.data.type.Door) chest.getBlockData();
        lower.setHalf(org.bukkit.block.data.Bisected.Half.BOTTOM);
        chest.setBlockData(lower, false);
        var upper = world.getBlockAt(x, y + 1, z);
        var upperData = (org.bukkit.block.data.type.Door) lower.clone();
        upperData.setHalf(org.bukkit.block.data.Bisected.Half.TOP);
        upper.setBlockData(upperData, false);
        var door =
            lands.protect(
                new ProtectionActor(owner, true, true, true, false),
                land.id(),
                "IRON_DOOR",
                ProtectionBlocks.part(chest).unit(),
                Map.of(),
                -1);
        for (Block half : List.of(chest, upper)) {
          var power = new BlockRedstoneEvent(half, 0, 15);
          listener.redstone(power);
          check(power.getNewCurrent() == 0, "Redstone blocked on both door halves");
          var physics = new BlockPhysicsEvent(half, Material.REDSTONE_BLOCK.createBlockData());
          listener.physics(physics);
          check(physics.isCancelled(), "Door physics protected");
        }
        var support = new BlockBreakEvent(chest.getRelative(BlockFace.DOWN), lockOwner);
        listener.breakBlock(support);
        check(support.isCancelled(), "Door supporting block protected");
        plugin.getConfig().set("block-protection.enabled", false);
        var offBreak = new BlockBreakEvent(chest, visitor);
        listener.breakBlock(offBreak);
        check(!offBreak.isCancelled(), "Disabled feature has no personal break denial");
        var offPower = new BlockRedstoneEvent(chest, 0, 15);
        listener.redstone(offPower);
        check(offPower.getNewCurrent() == 15, "Disabled feature leaves redstone untouched");
        check(
            lands.personalProtections().get(door.id()) != null, "Disabling preserves stored locks");
        plugin.getConfig().set("block-protection.enabled", true);
        check(
            service.denied(visitor, ProtectionBlocks.key(chest)),
            "Re-enable restores restrictions");
        var listMethod =
            ProtectionGui.class.getDeclaredMethod(
                "list", Player.class, UUID.class, boolean.class, UUID.class, int.class);
        listMethod.setAccessible(true);
        for (int i = 0; i < 51; i++) {
          var b =
              new BlockKey(world.getUID(), (x >> 4) * 16 + i % 8, y + 10, (z >> 4) * 16 + i / 8);
          lands.protect(
              new ProtectionActor(owner, true, true, true, false),
              land.id(),
              "BARREL",
              List.of(b),
              Map.of(),
              -1);
        }
        listMethod.invoke(gui, landOwner, land.id(), false, null, 0);
        check(top.getSize() == 54, "First protection-list page uses 45 content slots plus footer");
        check(
            top.getItem(50).getType() == Material.BLUE_STAINED_GLASS_PANE,
            "Next page uses final blue icon");
        for (int i = 45; i < 54; i++)
          check(
              top.getItem(i).getType() != Material.BARREL, "No protected-block entries in footer");
        listMethod.invoke(gui, landOwner, land.id(), false, null, 1);
        check(
            top.getSize() == 18 && top.getItem(12).getType() == Material.RED_STAINED_GLASS_PANE,
            "Small last page dynamically sized with red previous icon");
        for (int i = 0; i < 50; i++) lands.addMember(owner, UUID.randomUUID());
        var sharedMethod =
            ProtectionGui.class.getDeclaredMethod("shared", Player.class, UUID.class, int.class);
        sharedMethod.setAccessible(true);
        sharedMethod.invoke(gui, landOwner, door.id(), 0);
        check(
            top.getSize() == 54 && top.getItem(50).getType() == Material.BLUE_STAINED_GLASS_PANE,
            "Shared member pagination uses dedicated footer");
        for (int i = 45; i < 54; i++)
          check(
              top.getItem(i).getType() != Material.PLAYER_HEAD,
              "No member heads in navigation row");
        var selector = new ProtectionSelectionManager(plugin, lands, service, gui);
        gui.selection(selector);
        var pendingField = ProtectionSelectionManager.class.getDeclaredField("pending");
        pendingField.setAccessible(true);
        var pending = (Map<?, ?>) pendingField.get(selector);
        selector.start(lockOwner, land.id());
        check(pending.size() == 1, "Selection mode starts");
        sneaking = true;
        var cancel =
            new PlayerInteractEvent(
                lockOwner, Action.LEFT_CLICK_AIR, null, null, BlockFace.SELF, EquipmentSlot.HAND);
        cancel.setCancelled(true);
        selector.select(cancel);
        check(
            pending.isEmpty() && cancel.isCancelled(),
            "Sneak-left-click cancels even a vanilla-cancelled air action");
        sneaking = false;
        selector.start(lockOwner, land.id());
        selector.clear();
        check(pending.isEmpty(), "Reload cancellation clears all selection state");
      }
    } finally {
      blocks.forEach((b, data) -> b.setBlockData(data, false));
      old.forEach((k, v) -> plugin.getConfig().set(k, v));
    }
    return checks;
  }

  private void checkIcons(Material... icons) {
    for (var icon : icons)
      check(
          Arrays.stream(top.getContents()).anyMatch(i -> i.getType() == icon), "GUI icon " + icon);
    check(
        top.getItem(top.getSize() - 9).getType() == Material.ARROW
            && top.getItem(top.getSize() - 1).getType() == Material.BARRIER,
        "Footer navigation icons");
  }

  private Player player(UUID id, World world, int x, int y, int z) {
    return (Player)
        Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] {Player.class},
            (proxy, m, a) ->
                switch (m.getName()) {
                  case "getUniqueId" -> id;
                  case "getName" -> "LockProbe";
                  case "getLocale" -> "en_us";
                  case "getWorld" -> world;
                  case "getLocation" -> new Location(world, x, y, z);
                  case "isSneaking" -> sneaking;
                  case "hasPermission" -> !((String) a[0]).startsWith("landward.admin.") || admin;
                  case "openInventory" -> {
                    top = (Inventory) a[0];
                    yield null;
                  }
                  case "hashCode" -> id.hashCode();
                  case "equals" -> proxy == a[0];
                  case "toString" -> "LockProbe";
                  default ->
                      m.getReturnType() == boolean.class
                          ? false
                          : m.getReturnType() == int.class ? 0 : null;
                });
  }

  private Entity entity(World world) {
    return (Entity)
        Proxy.newProxyInstance(
            Entity.class.getClassLoader(),
            new Class<?>[] {Entity.class},
            (p, m, a) ->
                switch (m.getName()) {
                  case "getWorld" -> world;
                  case "getType" -> EntityType.TNT;
                  default ->
                      m.getReturnType() == boolean.class
                          ? false
                          : m.getReturnType() == int.class ? 0 : null;
                });
  }

  private void check(boolean okay, String detail) {
    checks++;
    if (!okay) throw new AssertionError(detail);
  }
}
