package io.github.devkruidenier.landward.blockprotection;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.gui.*;
import java.util.*;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.SkullMeta;

/** GUI actions are closures on an inventory owned by one viewer; no global player/slot state. */
public final class ProtectionGui implements Listener {
  private final LandWardPlugin plugin;
  private final LandManager lands;
  private final ProtectionService service;
  private ProtectionSelectionManager selection;

  public ProtectionGui(LandWardPlugin plugin, LandManager lands, ProtectionService service) {
    this.plugin = plugin;
    this.lands = lands;
    this.service = service;
  }

  public void closeAll() {
    for (var player : plugin.getServer().getOnlinePlayers())
      plugin
          .scheduler()
          .player(
              player,
              () -> {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder)
                  player.closeInventory();
              });
  }

  public void selection(ProtectionSelectionManager selection) {
    this.selection = selection;
  }

  private static final class Holder implements InventoryHolder {
    final UUID viewer;
    final Map<Integer, Runnable> actions = new HashMap<>();
    Inventory inventory;

    Holder(UUID viewer) {
      this.viewer = viewer;
    }

    @Override
    public Inventory getInventory() {
      return inventory;
    }
  }

  private String text(Player p, String key) {
    return plugin
        .localeService()
        .message(plugin.localeService().localeFor(p), "block-protection." + key);
  }

  private String text(Player p, String key, Map<String, ?> vars) {
    return plugin
        .localeService()
        .message(plugin.localeService().localeFor(p), "block-protection." + key, vars);
  }

  private Holder inventory(Player p, String title, int size, Runnable back) {
    Holder h = new Holder(p.getUniqueId());
    h.inventory = Bukkit.createInventory(h, size, text(p, title));
    GuiLayout.fill(h.inventory);
    button(h, size - 9, Material.ARROW, text(p, "back"), text(p, "back-hint"), back);
    button(
        h, size - 1, Material.BARRIER, text(p, "close"), text(p, "close-hint"), p::closeInventory);
    return h;
  }

  private void button(
      Holder h, int slot, Material material, String title, String hint, Runnable action) {
    h.inventory.setItem(slot, GuiText.item(material, title, NamedTextColor.AQUA, wrap(hint)));
    h.actions.put(slot, action);
  }

  private void centered(
      Holder h,
      int row,
      List<Runnable> actions,
      List<Material> icons,
      List<String> titles,
      List<String> hints) {
    for (int i = 0; i < actions.size(); i++)
      button(
          h,
          row * 9 + (9 - actions.size()) / 2 + i,
          icons.get(i),
          titles.get(i),
          hints.get(i),
          actions.get(i));
  }

  public ItemStack entry(Player p) {
    return GuiText.item(
        Material.TRIPWIRE_HOOK,
        text(p, "title"),
        NamedTextColor.AQUA,
        List.of(text(p, "open-hint")));
  }

  public void main(Player p, UUID land) {
    if (!service.visible(p, land)) {
      service.tell(p, "denied");
      return;
    }
    var h = inventory(p, "title", 27, () -> back(p, land));
    var a = service.actor(p);
    var actions = new ArrayList<Runnable>();
    var icons = new ArrayList<Material>();
    var titles = new ArrayList<String>();
    var hints = new ArrayList<String>();
    if (a.create() && lands.personalMember(land, a.id())) {
      actions.add(() -> selection.start(p, land));
      icons.add(Material.IRON_DOOR);
      titles.add(text(p, "protect"));
      hints.add(text(p, "protect-hint"));
    }
    if (a.own() && lands.personalMember(land, a.id())) {
      actions.add(() -> list(p, land, false, null, 0));
      icons.add(Material.CHEST);
      titles.add(text(p, "mine"));
      hints.add(text(p, "mine-hint"));
    }
    if (manageAll(p, land)) {
      actions.add(() -> list(p, land, true, null, 0));
      icons.add(Material.COMMAND_BLOCK);
      titles.add(text(p, "all"));
      hints.add(text(p, "all-hint"));
    }
    centered(h, 1, actions, icons, titles, hints);
    p.openInventory(h.inventory);
  }

  public boolean manageAll(Player p, UUID land) {
    var a = service.actor(p);
    return a.adminManage()
        || a.landManage() && lands.land(land).map(l -> l.ownerId().equals(a.id())).orElse(false);
  }

  private void back(Player p, UUID land) {
    if (service.actor(p).adminManage() && !lands.personalMember(land, p.getUniqueId()))
      new ClaimAdminGui(plugin, lands).openPlayerClaimDetails(p, land);
    else {
      var selected = lands.land(land).orElse(null);
      var gui = new LandManagementGui(plugin, lands);
      if (selected != null
          && !selected.ownerId().equals(p.getUniqueId())
          && lands.personalMember(land, p.getUniqueId())) gui.openMemberMain(p, selected);
      else gui.openMain(p);
    }
  }

  public void settings(Player p, ProtectionService.Selection selected) {
    if (!service.visible(p, selected.land())) return;
    var lock = lands.personalProtections().at(selected.blocks().getFirst());
    if (lock != null && !lands.managePersonal(service.actor(p), lock)) {
      service.tell(p, "denied");
      return;
    }
    var h = inventory(p, "title", 27, () -> main(p, selected.land()));
    Material material = Material.matchMaterial(selected.material());
    var info = new ItemStack(material == null || !material.isItem() ? Material.CHEST : material);
    var meta = info.getItemMeta();
    meta.setLore(
        List.of(
            text(p, "location", Map.of("location", selected.blocks().getFirst().label())),
            text(
                p,
                "land",
                Map.of("name", lands.land(selected.land()).map(l -> l.name()).orElse(""))),
            text(
                p,
                "owner",
                Map.of("name", lock == null ? name(p.getUniqueId()) : name(lock.owner()))),
            text(
                p, lock == null ? "unprotected" : lock.shared().isEmpty() ? "private" : "shared")));
    info.setItemMeta(meta);
    h.inventory.setItem(4, info);
    var actions = new ArrayList<Runnable>();
    var icons = new ArrayList<Material>();
    var titles = new ArrayList<String>();
    var hints = new ArrayList<String>();
    boolean edits =
        lock == null
            || lock.owner().equals(p.getUniqueId()) && service.actor(p).own()
            || service.actor(p).adminManage();
    if (edits) {
      actions.add(() -> {
        if (lock == null) service.create(p, selected, saved -> settings(p, selected));
        else {
          var a = service.actor(p);
          service.submit(
              p,
              () -> lands.sharePersonal(a, lock.id(), lock.revision(), Set.of()),
              () -> {
                service.tell(p, "updated");
                settings(p, selected);
              });
        }
      });
      icons.add(Material.IRON_BARS);
      titles.add(text(p, "private"));
      hints.add(text(p, "private-hint"));
      actions.add(() -> {
        if (lock == null) service.create(p, selected, saved -> shared(p, saved.id(), 0));
        else shared(p, lock.id(), 0);
      });
      icons.add(Material.PLAYER_HEAD);
      titles.add(text(p, "shared"));
      hints.add(text(p, "shared-hint"));
    }
    if (lock != null) {
      actions.add(() ->
          confirm(
              p,
              "remove-warning",
              () -> {
                var actor = service.actor(p);
                service.submit(
                    p,
                    () -> lands.removePersonal(actor, lock.id(), lock.revision()),
                    () -> {
                      service.tell(p, "removed");
                      main(p, selected.land());
                    });
              },
              () -> settings(p, selected)));
      icons.add(Material.HOPPER);
      titles.add(text(p, "remove"));
      hints.add(text(p, "remove-warning"));
    }
    centered(h, 1, actions, icons, titles, hints);
    p.openInventory(h.inventory);
  }

  public void openLock(Player p, BlockProtection lock) {
    if (!lands.managePersonal(service.actor(p), lock)) {
      service.tell(p, "denied");
      return;
    }
    // Record management/removal remains possible even when its physical block/world is obsolete.
    var generations = new HashMap<BlockKey, UUID>();
    for (var b : lock.blocks()) {
      var placed = lands.personalProtections().placement(b);
      if (placed != null) generations.put(b, placed.generation());
    }
    settings(
        p,
        new ProtectionService.Selection(
            lock.landId(), lock.material(), lock.blocks(), generations, ""));
  }

  private void list(Player p, UUID land, boolean all, UUID ownerFilter, int requested) {
    if (!service.visible(p, land) || all && !manageAll(p, land)) {
      service.tell(p, "denied");
      return;
    }
    var entries =
        lands.personalProtections().all().stream()
            .filter(l ->
                l.landId().equals(land)
                    && (all || l.owner().equals(p.getUniqueId()))
                    && (ownerFilter == null || l.owner().equals(ownerFilter)))
            .sorted(
                Comparator.comparing(BlockProtection::created).thenComparing(BlockProtection::id))
            .toList();
    int page = GuiGrid.page(requested, entries.size()), count = GuiGrid.count(page, entries.size());
    var h = inventory(p, all ? "all" : "mine", GuiGrid.size(count), () -> main(p, land));
    for (int i = 0; i < count; i++) {
      var lock = entries.get(GuiGrid.start(page, entries.size()) + i);
      Material m = Material.matchMaterial(lock.material());
      var item = new ItemStack(m == null || !m.isItem() ? Material.CHEST : m);
      var meta = item.getItemMeta();
      meta.setLore(
          List.of(
              text(p, "owner", Map.of("name", name(lock.owner()))),
              text(p, "location", Map.of("location", lock.blocks().getFirst().label())),
              text(p, lock.shared().isEmpty() ? "private" : "shared"),
              text(p, "shared-count", Map.of("count", lock.shared().size())),
              text(p, "edit-hint")));
      item.setItemMeta(meta);
      int slot = GuiGrid.slot(i, count);
      h.inventory.setItem(slot, item);
      h.actions.put(slot, () -> openLock(p, lock));
    }
    if (count == 0)
      h.inventory.setItem(
          4,
          GuiText.item(
              Material.GRAY_STAINED_GLASS_PANE, text(p, "empty"), NamedTextColor.GRAY, List.of()));
    pages(p, h, page, entries.size(), v -> list(p, land, all, ownerFilter, v));
    if (all)
      button(
          h,
          h.inventory.getSize() - 5,
          Material.PLAYER_HEAD,
          text(p, "filter"),
          text(p, "filter-hint"),
          () -> owners(p, land, 0));
    p.openInventory(h.inventory);
  }

  private void owners(Player p, UUID land, int requested) {
    if (!service.visible(p, land) || !manageAll(p, land)) return;
    var owners =
        lands.personalProtections().all().stream()
            .filter(l -> l.landId().equals(land))
            .map(BlockProtection::owner)
            .distinct()
            .sorted()
            .toList();
    int page = GuiGrid.page(requested, owners.size()), count = GuiGrid.count(page, owners.size());
    var h = inventory(p, "filter", GuiGrid.size(count), () -> list(p, land, true, null, 0));
    for (int i = 0; i < count; i++) {
      UUID id = owners.get(GuiGrid.start(page, owners.size()) + i);
      int slot = GuiGrid.slot(i, count);
      h.inventory.setItem(slot, head(p, id, text(p, "filter-hint")));
      h.actions.put(slot, () -> list(p, land, true, id, 0));
    }
    pages(p, h, page, owners.size(), v -> owners(p, land, v));
    p.openInventory(h.inventory);
  }

  private void shared(Player p, UUID id, int requested) {
    var lock = lands.personalProtections().get(id);
    var actor = service.actor(p);
    if (lock == null
        || !service.visible(p, lock.landId())
        || !(actor.adminManage() || lock.owner().equals(actor.id()) && actor.own())) return;
    var ids = new TreeSet<UUID>();
    lands.members(lock.landId()).forEach(m -> ids.add(m.playerId()));
    lands.land(lock.landId()).ifPresent(l -> ids.add(l.ownerId()));
    ids.remove(lock.owner());
    var members = List.copyOf(ids);
    int page = GuiGrid.page(requested, members.size()), count = GuiGrid.count(page, members.size());
    var h = inventory(p, "shared", GuiGrid.size(count), () -> openLock(p, lock));
    for (int i = 0; i < count; i++) {
      UUID member = members.get(GuiGrid.start(page, members.size()) + i);
      int slot = GuiGrid.slot(i, count);
      h.inventory.setItem(
          slot,
          head(
              p,
              member,
              text(p, lock.shared().contains(member) ? "granted" : "denied-access")
                  + "\n"
                  + text(p, "toggle-hint")));
      h.actions.put(
          slot,
          () -> {
            var current = lands.personalProtections().get(id);
            if (current == null) return;
            var shared = new HashSet<>(current.shared());
            if (!shared.remove(member)) shared.add(member);
            var a = service.actor(p);
            service.submit(
                p,
                () -> lands.sharePersonal(a, id, current.revision(), shared),
                () -> shared(p, id, page));
          });
    }
    if (count == 0)
      h.inventory.setItem(
          4,
          GuiText.item(
              Material.GRAY_STAINED_GLASS_PANE,
              text(p, "no-members"),
              NamedTextColor.GRAY,
              List.of()));
    pages(p, h, page, members.size(), v -> shared(p, id, v));
    p.openInventory(h.inventory);
  }

  private void pages(Player p, Holder h, int page, int total, java.util.function.IntConsumer open) {
    int end = h.inventory.getSize();
    if (page > 0)
      button(
          h,
          end - 6,
          Material.RED_STAINED_GLASS_PANE,
          text(p, "previous"),
          text(p, "previous-hint"),
          () -> open.accept(page - 1));
    if (page + 1 < GuiGrid.pages(total))
      button(
          h,
          end - 4,
          Material.BLUE_STAINED_GLASS_PANE,
          text(p, "next"),
          text(p, "next-hint"),
          () -> open.accept(page + 1));
  }

  private ItemStack head(Player p, UUID id, String state) {
    var item =
        GuiText.item(
            Material.PLAYER_HEAD, name(id), NamedTextColor.AQUA, Arrays.asList(state.split("\n")));
    var meta = (SkullMeta) item.getItemMeta();
    meta.setOwningPlayer(Bukkit.getOfflinePlayer(id));
    item.setItemMeta(meta);
    return item;
  }

  private static List<String> wrap(String text) {
    var result = new ArrayList<String>();
    for (var line : text.split("\n")) {
      while (line.length() > 48) {
        int cut = line.lastIndexOf(' ', 48);
        if (cut < 16) cut = 48;
        result.add(line.substring(0, cut));
        line = line.substring(cut).stripLeading();
      }
      if (!line.isEmpty()) result.add(line);
    }
    return result;
  }

  private String name(UUID id) {
    String name = Bukkit.getOfflinePlayer(id).getName();
    return name == null ? id.toString() : name;
  }

  public void confirm(Player p, String warning, Runnable yes, Runnable back) {
    var h = inventory(p, "confirm", 27, back);
    h.inventory.setItem(
        4,
        GuiText.item(
            Material.IRON_BARS, text(p, "confirm"), NamedTextColor.YELLOW, wrap(text(p, warning))));
    button(h, 12, Material.LIME_CONCRETE, text(p, "confirm"), text(p, "confirm-hint"), yes);
    button(h, 13, Material.RED_CONCRETE, text(p, "cancel"), text(p, "cancel-hint"), back);
    p.openInventory(h.inventory);
  }

  public void leave(Player p, UUID land) {
    long count =
        lands.personalProtections().all().stream()
            .filter(l -> l.landId().equals(land) && l.owner().equals(p.getUniqueId()))
            .count();
    service.tell(p, "leave-count", Map.of("count", count));
    confirm(
        p,
        "leave-warning",
        () -> {
          if (plugin.combatTags().blocksClaimAbandon(p)) {
            p.closeInventory();
            return;
          }
          var h = service.actor(p);
          service.submit(
              p,
              () -> lands.leavePersonalLand(h.id(), land),
              () -> {
                service.tell(p, "cleanup-count", Map.of("count", count));
                new LandManagementGui(plugin, lands).openMain(p);
              });
        },
        () -> back(p, land));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void click(InventoryClickEvent e) {
    if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
    e.setCancelled(true);
    if (!(e.getWhoClicked() instanceof Player p)
        || !h.viewer.equals(p.getUniqueId())
        || e.getRawSlot() < 0
        || e.getRawSlot() >= h.inventory.getSize()) return;
    var action = h.actions.get(e.getRawSlot());
    if (action != null)
      plugin
          .scheduler()
          .player(
              p,
              () -> {
                if (p.getOpenInventory().getTopInventory() == h.inventory) action.run();
              });
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void drag(InventoryDragEvent e) {
    if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
  }
}
