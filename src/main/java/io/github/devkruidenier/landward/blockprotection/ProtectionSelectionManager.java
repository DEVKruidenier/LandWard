package io.github.devkruidenier.landward.blockprotection;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;

/** A short-lived selection is independent of block ownership and vanishes on logout. */
public final class ProtectionSelectionManager implements Listener {
  private record Pending(UUID land, long expires) {}

  private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
  private final LandWardPlugin plugin;
  private final LandManager lands;
  private final ProtectionService service;
  private final ProtectionGui gui;

  public ProtectionSelectionManager(
      LandWardPlugin plugin, LandManager lands, ProtectionService service, ProtectionGui gui) {
    this.plugin = plugin;
    this.lands = lands;
    this.service = service;
    this.gui = gui;
    plugin
        .scheduler()
        .globalRepeat(
            () -> {
              long now = System.nanoTime();
              pending.forEach((id, s) -> {
                if (now >= s.expires() && pending.remove(id, s)) {
                  var p = plugin.getServer().getPlayer(id);
                  if (p != null) plugin.scheduler().player(p, () -> service.tell(p, "timeout"));
                }
              });
            },
            20,
            20);
  }

  public void start(Player p, UUID land) {
    if (!service.visible(p, land) || !service.actor(p).create()) return;
    p.closeInventory();
    pending.put(p.getUniqueId(), new Pending(land, System.nanoTime() + 60_000_000_000L));
    service.tell(p, "select");
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void select(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND) return;
    var p = e.getPlayer();
    var selected = pending.get(p.getUniqueId());
    if (selected == null) return;
    if (!service.enabled() || System.nanoTime() >= selected.expires()) {
      pending.remove(p.getUniqueId());
      return;
    }
    if (p.isSneaking()
        && (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK)) {
      e.setCancelled(true);
      pending.remove(p.getUniqueId());
      service.tell(p, "cancelled");
      return;
    }
    if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
    if (e.useInteractedBlock() == Event.Result.DENY) {
      service.tell(p, "denied");
      return;
    }
    e.setCancelled(true);
    if (!lands.personalMember(selected.land(), p.getUniqueId())
        || !service.actor(p).create()
        || !plugin.isLandWorld(p.getWorld())) {
      service.tell(p, "denied");
      return;
    }
    var b = e.getClickedBlock();
    if (!ProtectionBlocks.supported(b.getType())) {
      service.tell(p, "unsupported");
      return;
    }
    if (!lands
        .landAt(ProtectionBlocks.key(b).chunk())
        .map(l -> l.id().equals(selected.land()))
        .orElse(false)) {
      service.tell(p, "outside");
      return;
    }
    pending.remove(p.getUniqueId());
    service
        .inspect(selected.land(), ProtectionBlocks.key(b))
        .whenComplete((snapshot, error) ->
            plugin
                .scheduler()
                .player(
                    p,
                    () -> {
                      if (!service.enabled()) return;
                      if (error != null) {
                        service.tell(p, "changed");
                        return;
                      }
                      gui.settings(p, snapshot);
                    }));
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    pending.remove(e.getPlayer().getUniqueId());
  }

  public void clear() {
    pending.clear();
  }
}
