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
  private Object oldEnabled;
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
      plugin.getConfig().set("block-protection.enabled", true);
      var dir = probe.getDataFolder().toPath();
      java.nio.file.Files.createDirectories(dir);
      db = new SqliteLandStorage(dir.resolve("native-" + UUID.randomUUID() + ".db"));
      db.initialize();
      var lands = new LandManager(db);
      lands.load();
      UUID owner = UUID.randomUUID();
      var land =
          lands
              .create(owner, "Native Lock Probe", new ChunkPosition(world.getUID(), x >> 4, z >> 4))
              .land();
      chest = world.getBlockAt(x, y, z);
      chest.setType(Material.CHEST, false);
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
      lands.protect(actor, land.id(), "CHEST", List.of(ProtectionBlocks.key(chest)), Map.of(), -1);
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
                        case "isOnline" -> true;
                        case "getLocale" -> "en_us";
                        case "hashCode" -> owner.hashCode();
                        case "equals" -> p == a[0];
                        default ->
                            m.getReturnType() == boolean.class
                                ? false
                                : m.getReturnType() == int.class ? 0 : null;
                      });
      power = lower.getRelative(BlockFace.EAST);
      power.setType(Material.REDSTONE_BLOCK, true);
      later(
          40,
          () -> {
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
            power.setType(Material.AIR, true);
            click();
            later(
                10,
                () -> {
                  check(
                      ((Door) lower.getBlockData()).isOpen()
                          && ((Door) upper.getBlockData()).isOpen(),
                      "Permitted player interaction opens both iron door halves");
                  click();
                  later(
                      10,
                      () -> {
                        check(
                            !((Door) lower.getBlockData()).isOpen(),
                            "Permitted player closes iron door");
                        finish(null);
                      });
                });
          });
    } catch (Throwable failure) {
      finish(failure);
    }
  }

  private void click() {
    var event =
        new PlayerInteractEvent(
            player, Action.RIGHT_CLICK_BLOCK, null, lower, BlockFace.NORTH, EquipmentSlot.HAND);
    Bukkit.getPluginManager().callEvent(event);
    check(!event.isCancelled(), "Authorized click not denied");
  }

  private void later(int ticks, Runnable action) {
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
      if (oldEnabled != null) plugin.getConfig().set("block-protection.enabled", oldEnabled);
    } catch (Throwable cleanup) {
      if (error == null) error = cleanup;
      else error.addSuppressed(cleanup);
    }
    if (error == null) probe.getLogger().info("NATIVELOCKS-PROBE-PASS assertions=" + checks);
    else probe.getLogger().log(java.util.logging.Level.SEVERE, "NATIVELOCKS-PROBE-FAIL", error);
  }
}
