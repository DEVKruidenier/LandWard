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
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Runs real hopper ticks and powered-door updates in the isolated fixture world. */
public final class NativeProtectionProbe {
  public static void run(LandWardPlugin plugin, JavaPlugin probe) {
    new NativeProtectionProbe(plugin, probe).start();
  }

  private final LandWardPlugin plugin;
  private final JavaPlugin probe;
  private final Map<Block, org.bukkit.block.data.BlockData> previous = new LinkedHashMap<>();
  private ProtectionListener listener;
  private ProtectionService service;
  private SqliteLandStorage db;
  private LandManager lands;
  private Object oldEnabled;
  private Object oldAutomatic;
  private boolean online = true;
  private Block chest, hopper, lower, upper, power;
  private Player player;
  private org.bukkit.Chunk chunk;
  private boolean forced;
  private int checks;

  private NativeProtectionProbe(LandWardPlugin plugin, JavaPlugin probe) {
    this.plugin = plugin;
    this.probe = probe;
  }

  private void start() {
    try {
      var world = Bukkit.getWorlds().getFirst();
      int x = 28008, y = 110, z = 28008;
      chunk = world.getChunkAt(x >> 4, z >> 4);
      forced = chunk.isForceLoaded();
      chunk.setForceLoaded(true);
      for (int dx = -1; dx < 7; dx++)
        for (int dy = -1; dy < 3; dy++)
          for (int dz = -1; dz < 2; dz++) {
            var b = world.getBlockAt(x + dx, y + dy, z + dz);
            previous.put(b, b.getBlockData());
            b.setType(Material.AIR, false);
          }
      oldEnabled = plugin.getConfig().get("block-protection.enabled");
      oldAutomatic = plugin.getConfig().get("block-protection.auto-protect");
      plugin.getConfig().set("block-protection.enabled", true);
      var dir = probe.getDataFolder().toPath();
      java.nio.file.Files.createDirectories(dir);
      db = new SqliteLandStorage(dir.resolve("native-" + UUID.randomUUID() + ".db"));
      db.initialize();
      lands = new LandManager(db);
      lands.load();
      UUID owner = UUID.randomUUID();
      var land =
          lands
              .create(owner, "Native Lock Probe", new ChunkPosition(world.getUID(), x >> 4, z >> 4))
              .land();
      chest = world.getBlockAt(x, y, z);
      chest.setType(Material.COPPER_CHEST, false);
      hopper = world.getBlockAt(x, y - 1, z);
      hopper.setType(Material.HOPPER, false);
      ((org.bukkit.block.Chest) chest.getState())
          .getBlockInventory()
          .setItem(0, new ItemStack(Material.DIAMOND, 4));
      lower = world.getBlockAt(x + 4, y, z);
      upper = world.getBlockAt(x + 4, y + 1, z);
      lower.getRelative(BlockFace.DOWN).setType(Material.STONE, false);
      Door bottom = (Door) Material.IRON_DOOR.createBlockData();
      bottom.setHalf(Bisected.Half.BOTTOM);
      lower.setBlockData(bottom, false);
      Door top = (Door) bottom.clone();
      top.setHalf(Bisected.Half.TOP);
      upper.setBlockData(top, false);
      var actor = new ProtectionActor(owner, true, true, true, false);
      lands.protect(
          actor, land.id(), "COPPER_CHEST", List.of(ProtectionBlocks.key(chest)), Map.of(), -1);
      lands.protect(
          actor,
          land.id(),
          "IRON_DOOR",
          List.of(ProtectionBlocks.key(lower), ProtectionBlocks.key(upper)),
          Map.of(),
          -1);
      service = new ProtectionService(plugin, lands);
      listener = new ProtectionListener(plugin, lands, service);
      Bukkit.getPluginManager().registerEvents(listener, probe);
      player =
          (Player)
              Proxy.newProxyInstance(
                  Player.class.getClassLoader(),
                  new Class<?>[] {Player.class},
                  (p, m, a) ->
                      switch (m.getName()) {
                        case "getUniqueId" -> owner;
                        case "getName" -> "NativeLockProbe";
                        case "getWorld" -> world;
                        case "getLocation" -> lower.getLocation();
                        case "isOnline" -> online;
                        case "hasPermission" -> ((String) a[0]).startsWith("landward.protection.");
                        case "getLocale" -> "en_us";
                        case "hashCode" -> owner.hashCode();
                        case "equals" -> p == a[0];
                        default ->
                            m.getReturnType() == boolean.class
                                ? false
                                : m.getReturnType() == int.class ? 0 : null;
                      });
      plugin.getConfig().set("block-protection.auto-protect", true);
      var automatic = world.getBlockAt(x + 2, y, z);
      var before = automatic.getState();
      automatic.setType(Material.BARREL, false);
      var autoKey = ProtectionBlocks.key(automatic);
      var place =
          new org.bukkit.event.block.BlockPlaceEvent(
              automatic,
              before,
              automatic.getRelative(BlockFace.DOWN),
              new ItemStack(Material.BARREL),
              player,
              true,
              EquipmentSlot.HAND);
      Bukkit.getPluginManager().callEvent(place);
      check(
          !place.isCancelled() && service.mechanical(autoKey),
          "Automatic placement is reserved before persistence completes");
      online = false;
      power = lower.getRelative(BlockFace.EAST);
      power.setType(Material.REDSTONE_BLOCK, true);
      later(
          40,
          () -> {
            check(
                lands.personalProtections().at(autoKey) != null,
                "Automatic lock persists even after the placing player disconnects");
            online = true;
            check(
                !((Door) lower.getBlockData()).isOpen() && !((Door) upper.getBlockData()).isOpen(),
                "Native redstone must not open either door half");
            check(
                ((org.bukkit.block.Chest) chest.getState())
                        .getBlockInventory()
                        .getItem(0)
                        .getAmount()
                    == 4,
                "Native hopper cannot extract protected items");
            check(
                ((Hopper) hopper.getState()).getInventory().isEmpty(),
                "Native hopper inventory stays empty");
            verifyOwnedHopper(lands, land.id(), owner);
          });
    } catch (Throwable failure) {
      finish(failure);
    }
  }

  private void verifyDoors() {
    power.setType(Material.AIR, true);
    click();
    later(
        10,
        () -> {
          check(
              ((Door) lower.getBlockData()).isOpen() && ((Door) upper.getBlockData()).isOpen(),
              "Permitted player interaction opens both iron door halves");
          click();
          later(
              10,
              () -> {
                check(!((Door) lower.getBlockData()).isOpen(), "Permitted player closes iron door");
                verifyVanillaDoors(0);
              });
        });
  }

  private void verifyVanillaDoors(int index) throws Exception {
    var materials = List.of(Material.ACACIA_DOOR, Material.COPPER_DOOR);
    if (index == materials.size()) {
      verifyStorageResize();
      return;
    }
    var existing = lands.personalProtections().at(ProtectionBlocks.key(lower));
    var actor = new ProtectionActor(player.getUniqueId(), true, true, true, false);
    lands.removePersonal(actor, existing.id(), existing.revision());
    Door bottom = (Door) materials.get(index).createBlockData();
    bottom.setHalf(Bisected.Half.BOTTOM);
    lower.setBlockData(bottom, false);
    Door top = (Door) bottom.clone();
    top.setHalf(Bisected.Half.TOP);
    upper.setBlockData(top, false);
    lands.protect(
        actor,
        existing.landId(),
        materials.get(index).name(),
        List.of(ProtectionBlocks.key(lower), ProtectionBlocks.key(upper)),
        Map.of(),
        -1);
    // PlayerInteractEvent precedes vanilla's clicked-half write. Physics is deliberately
    // denied by the lock, so reproduce that write without updating the other half.
    click(lower);
    bottom.setOpen(true);
    lower.setBlockData(bottom, false);
    later(
        10,
        () -> {
          check(
              ((Door) lower.getBlockData()).isOpen() && ((Door) upper.getBlockData()).isOpen(),
              "Both halves open after native lower-half click: " + materials.get(index));
          click(upper);
          Door upperClosed = (Door) upper.getBlockData();
          upperClosed.setOpen(false);
          upper.setBlockData(upperClosed, false);
          later(
              10,
              () -> {
                check(
                    !((Door) lower.getBlockData()).isOpen()
                        && !((Door) upper.getBlockData()).isOpen(),
                    "Both halves close after native upper-half click: " + materials.get(index));
                verifyVanillaDoors(index + 1);
              });
        });
  }

  private void verifyStorageResize() throws Exception {
    hopper.setType(Material.AIR, false);
    var existing = lands.personalProtections().at(ProtectionBlocks.key(chest));
    var second = chest.getRelative(BlockFace.EAST);
    var before = second.getState();
    org.bukkit.block.data.type.Chest data =
        (org.bukkit.block.data.type.Chest) Material.COPPER_CHEST.createBlockData();
    data.setFacing(BlockFace.NORTH);
    data.setType(org.bukkit.block.data.type.Chest.Type.RIGHT);
    second.setBlockData(data, false);
    var placement =
        new org.bukkit.event.block.BlockPlaceEvent(
            second,
            before,
            chest,
            new ItemStack(Material.COPPER_CHEST),
            player,
            true,
            EquipmentSlot.HAND);
    Bukkit.getPluginManager().callEvent(placement);
    check(!placement.isCancelled(), "Owner's native copper chest extension is allowed");
    check(
        service.uncertain(ProtectionBlocks.key(second)),
        "New half is reserved before the async protection write");
    later(
        20,
        () -> {
          var expanded = lands.personalProtections().at(ProtectionBlocks.key(second));
          check(
              expanded != null
                  && expanded.id().equals(existing.id())
                  && expanded.blocks().size() == 2
                  && expanded.shared().equals(existing.shared()),
              "Expanded chest preserves lock identity and sharing");
          check(
              ((org.bukkit.block.Chest) second.getState()).getInventory().getSize() == 54,
              "Expanded native chest has one double inventory");
          ((org.bukkit.block.Chest) second.getState())
              .getBlockInventory()
              .setItem(0, new ItemStack(Material.EMERALD, 3));
          var breaking = new org.bukkit.event.block.BlockBreakEvent(chest, player);
          Bukkit.getPluginManager().callEvent(breaking);
          check(!breaking.isCancelled(), "Owner may break one native protected chest half");
          chest.setType(Material.AIR, false);
          later(
              20,
              () -> {
                var remaining = lands.personalProtections().at(ProtectionBlocks.key(second));
                check(
                    remaining != null
                        && remaining.id().equals(existing.id())
                        && remaining.blocks().equals(List.of(ProtectionBlocks.key(second)))
                        && remaining.shared().equals(existing.shared()),
                    "Remaining half retains original access");
                check(
                    ((org.bukkit.block.Chest) second.getState()).getInventory().getSize() == 27
                        && ((org.bukkit.block.Chest) second.getState())
                            .getBlockInventory()
                            .contains(Material.EMERALD, 3),
                    "Remaining native half keeps its contents and becomes single");
                var last = new org.bukkit.event.block.BlockBreakEvent(second, player);
                Bukkit.getPluginManager().callEvent(last);
                check(!last.isCancelled(), "Owner may break the last native protected chest");
                second.setType(Material.AIR, false);
                later(
                    20,
                    () -> {
                      check(
                          lands.personalProtections().get(existing.id()) == null,
                          "Breaking the final half removes the lock");
                      finish(null);
                    });
              });
        });
  }

  private void verifyOwnedHopper(LandManager lands, UUID land, UUID owner) throws Exception {
    var key = ProtectionBlocks.key(hopper);
    lands.recordPlacement(new BlockPlacement(key, land, owner, "HOPPER", UUID.randomUUID()));
    later(
        40,
        () -> {
          check(
              ((Hopper) hopper.getState()).getInventory().contains(Material.DIAMOND),
              "Native hopper owned by lock owner extracts protected items");
          UUID visitorId = UUID.randomUUID();
          var handler = Proxy.getInvocationHandler(player);
          var visitor =
              (Player)
                  Proxy.newProxyInstance(
                      Player.class.getClassLoader(),
                      new Class<?>[] {Player.class},
                      (proxy, m, args) ->
                          m.getName().equals("getUniqueId")
                              ? visitorId
                              : handler.invoke(proxy, m, args));
          var inventory = ((Hopper) hopper.getState()).getInventory();
          var view =
              (org.bukkit.inventory.InventoryView)
                  Proxy.newProxyInstance(
                      org.bukkit.inventory.InventoryView.class.getClassLoader(),
                      new Class<?>[] {org.bukkit.inventory.InventoryView.class},
                      (proxy, m, args) ->
                          switch (m.getName()) {
                            case "getPlayer" -> visitor;
                            case "getTopInventory" -> inventory;
                            case "getBottomInventory" -> Bukkit.createInventory(null, 36);
                            case "getType" -> org.bukkit.event.inventory.InventoryType.HOPPER;
                            default -> null;
                          });
          var open = new org.bukkit.event.inventory.InventoryOpenEvent(view);
          listener.open(open);
          check(
              open.isCancelled(),
              "Other players cannot steal extracted items by opening the owned hopper");
          var destroy = new org.bukkit.event.block.BlockBreakEvent(hopper, visitor);
          listener.breakBlock(destroy);
          check(destroy.isCancelled(), "Other players cannot break the private automation hopper");
          var ownBreak = new org.bukkit.event.block.BlockBreakEvent(hopper, player);
          listener.breakBlock(ownBreak);
          check(!ownBreak.isCancelled(), "The placing owner can move their own hopper");
          var output = hopper.getRelative(BlockFace.DOWN);
          output.setType(Material.HOPPER, false);
          UUID other = UUID.randomUUID();
          lands.addMember(owner, other);
          lands.recordPlacement(
              new BlockPlacement(
                  ProtectionBlocks.key(output), land, other, "HOPPER", UUID.randomUUID()));
          later(
              20,
              () -> {
                check(
                    ((Hopper) output.getState()).getInventory().isEmpty(),
                    "A different owner's native hopper cannot steal from the private hopper");
                lands.forgetPersonal(List.of(ProtectionBlocks.key(output)));
                output.setType(Material.CHEST, false);
                later(
                    20,
                    () -> {
                      check(
                          ((org.bukkit.block.Chest) output.getState())
                              .getBlockInventory()
                              .isEmpty(),
                          "Unprotected output storage cannot expose the private pipeline");
                      lands.protect(
                          new ProtectionActor(owner, true, true, true, false),
                          land,
                          "CHEST",
                          List.of(ProtectionBlocks.key(output)),
                          Map.of(),
                          -1);
                      later(
                          20,
                          () -> {
                            check(
                                ((org.bukkit.block.Chest) output.getState())
                                    .getBlockInventory()
                                    .contains(Material.DIAMOND),
                                "Same-owner protected output storage accepts the hopper items");
                            lands.addMember(owner, visitorId);
                            var source =
                                lands.personalProtections().at(ProtectionBlocks.key(chest));
                            var actor = new ProtectionActor(owner, true, true, true, false);
                            lands.sharePersonal(
                                actor, source.id(), source.revision(), Set.of(visitorId));
                            service.automation(key);
                            later(
                                20,
                                () -> {
                                  var partial =
                                      new org.bukkit.event.inventory.InventoryOpenEvent(view);
                                  listener.open(partial);
                                  check(
                                      partial.isCancelled(),
                                      "Sharing one source cannot expose a different unshared source"
                                          + " through its hopper");
                                  var destination =
                                      lands.personalProtections().at(ProtectionBlocks.key(output));
                                  lands.sharePersonal(
                                      actor,
                                      destination.id(),
                                      destination.revision(),
                                      Set.of(visitorId));
                                  service.automation(key);
                                  later(
                                      20,
                                      () -> {
                                        var shared =
                                            new org.bukkit.event.inventory.InventoryOpenEvent(view);
                                        listener.open(shared);
                                        check(
                                            !shared.isCancelled(),
                                            "Sharing all source inventories grants eligible member"
                                                + " access to the hopper");
                                        lands.updateMemberPermission(
                                            owner,
                                            visitorId,
                                            io.github.devkruidenier.landward.model.MemberPermissions
                                                .Permission.CONTAINERS,
                                            false);
                                        var denied =
                                            new org.bukkit.event.inventory.InventoryOpenEvent(view);
                                        listener.open(denied);
                                        check(
                                            denied.isCancelled(),
                                            "Shared hopper access never overrides denied normal"
                                                + " container rights");
                                        verifyDoors();
                                      });
                                });
                          });
                    });
              });
        });
  }

  private void click() {
    click(lower);
  }

  private void click(Block target) {
    var event =
        new PlayerInteractEvent(
            player, Action.RIGHT_CLICK_BLOCK, null, target, BlockFace.NORTH, EquipmentSlot.HAND);
    Bukkit.getPluginManager().callEvent(event);
    check(!event.isCancelled(), "Authorized click not denied");
  }

  @FunctionalInterface
  private interface CheckedStep {
    void run() throws Exception;
  }

  private void later(int ticks, CheckedStep action) {
    plugin
        .scheduler()
        .globalLater(
            () -> {
              try {
                action.run();
              } catch (Throwable e) {
                finish(e);
              }
            },
            ticks);
  }

  private void check(boolean pass, String message) {
    checks++;
    if (!pass) throw new AssertionError(message);
  }

  private void finish(Throwable error) {
    try {
      if (listener != null) HandlerList.unregisterAll(listener);
      if (service != null) service.close();
      if (db != null) db.close();
      previous.forEach((b, data) -> b.setBlockData(data, false));
      if (chunk != null) chunk.setForceLoaded(forced);
      if (oldAutomatic != null)
        plugin.getConfig().set("block-protection.auto-protect", oldAutomatic);
      if (oldEnabled != null) plugin.getConfig().set("block-protection.enabled", oldEnabled);
    } catch (Throwable cleanup) {
      if (error == null) error = cleanup;
      else error.addSuppressed(cleanup);
    }
    if (error == null) probe.getLogger().info("NATIVELOCKS-PROBE-PASS assertions=" + checks);
    else probe.getLogger().log(java.util.logging.Level.SEVERE, "NATIVELOCKS-PROBE-FAIL", error);
  }
}
