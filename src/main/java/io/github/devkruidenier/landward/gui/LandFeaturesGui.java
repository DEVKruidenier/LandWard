package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.view.AnvilView;
import java.util.*;

/** Separate land-wide environment controls from member access and warp management. */
public final class LandFeaturesGui implements Listener {
    private final LandWardPlugin plugin; private final LandManager lands; private final WarpTeleportService teleports;
    private final Map<UUID, Input> inputs = new HashMap<>();
    private record Input(UUID land, UUID warp, AnvilView view) {}
    private enum Screen { CATEGORIES, ENVIRONMENT, WARPS, WARP, DELETE }
    private static final class Holder implements InventoryHolder {
        final UUID land; final boolean admin; final Screen screen; final int page; final UUID warp;
        final Map<Integer, UUID> entries = new HashMap<>(); Inventory inventory;
        Holder(UUID land, boolean admin, Screen screen, int page, UUID warp) {
            this.land = land; this.admin = admin; this.screen = screen; this.page = page; this.warp = warp;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
    public LandFeaturesGui(LandWardPlugin plugin, LandManager lands) {
        this.plugin = plugin; this.lands = lands; this.teleports = new WarpTeleportService(plugin, lands);
        plugin.getServer().getPluginManager().registerEvents(teleports, plugin);
    }
    public void open(Player player, UUID land, boolean admin) {
        boolean owner = lands.land(land).map(value -> value.ownerId().equals(player.getUniqueId())).orElse(false);
        open(player, new Holder(land, admin, !admin && !owner ? Screen.WARPS : Screen.CATEGORIES, 0, null));
    }
    public void openWarps(Player player, UUID land, boolean admin) {
        open(player, new Holder(land, admin, Screen.WARPS, 0, null));
    }
    private boolean authorized(Player player, UUID land, boolean admin) {
        return plugin.isLandWorld(player.getWorld()) && lands.land(land).isPresent()
                && (admin ? player.hasPermission("landward.admin") : lands.canUseWarps(player.getUniqueId(), land));
    }
    private boolean manages(Player player, Holder holder) {
        return authorized(player, holder.land, holder.admin) && (holder.admin
                || lands.land(holder.land).orElseThrow().ownerId().equals(player.getUniqueId()));
    }
    private void open(Player player, Holder holder) {
        if (!authorized(player, holder.land, holder.admin)) { player.closeInventory(); return; }
        boolean owner = !holder.admin && manages(player, holder);
        int size = holder.screen == Screen.ENVIRONMENT ? 36 : holder.screen == Screen.WARPS ? 54 : 27;
        holder.inventory = Bukkit.createInventory(holder, size, text(player, holder.screen == Screen.ENVIRONMENT ? "features.environment" : holder.screen == Screen.CATEGORIES ? "features.categories" : "warps.title"));
        for (int slot = 0; slot < size; slot++) holder.inventory.setItem(slot, item(player, Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        holder.inventory.setItem(size - 9, button(player, Material.ARROW, "gui.management-back", "features.back-hint"));
        holder.inventory.setItem(size - 1, button(player, Material.BARRIER, "gui.close", "features.close-hint"));
        switch (holder.screen) {
            case CATEGORIES -> {
                holder.inventory.setItem(12, button(player, Material.PLAYER_HEAD, "features.access", "features.access-hint"));
                if (manages(player, holder)) holder.inventory.setItem(13, button(player, Material.FLINT_AND_STEEL, "features.environment", "features.environment-hint"));
            }
            case ENVIRONMENT -> {
                if (!manages(player, holder)) return;
                var settings = lands.features(holder.land).environment();
                var rules = EnvironmentSettings.Rule.values();
                Material[] icons = {Material.FLINT_AND_STEEL, Material.FEATHER, Material.TNT, Material.FIRE_CHARGE,
                        Material.SHIELD, Material.CAMPFIRE, Material.LAVA_BUCKET, Material.GRASS_BLOCK, Material.WATER_BUCKET, Material.BUCKET};
                for (int i = 0; i < rules.length; i++) {
                    int slot = FeatureMenuLayout.environmentSlot(i);
                    String key = "features." + rules[i].name().toLowerCase(Locale.ROOT).replace('_', '-');
                    holder.inventory.setItem(slot, item(player, icons[i], text(player, key), List.of(
                            text(player, settings.allows(rules[i]) ? "features.allowed" : "features.denied"),
                            text(player, key + "-hint"), text(player, "features.scope"), text(player, "features.toggle"))));
                }
            }
            case WARPS -> {
                var list = lands.features(holder.land).warps().stream().sorted(Comparator.comparing(LandWarp::name, String.CASE_INSENSITIVE_ORDER)).toList();
                int page = Math.min(holder.page, Math.max(0, (list.size() - 1) / 45));
                if (page != holder.page) { open(player, new Holder(holder.land, holder.admin, Screen.WARPS, page, null)); return; }
                int count = Math.min(45, list.size() - page * 45);
                for (int i = 0; i < count; i++) {
                    int slot = FeatureMenuLayout.listSlot(i, count);
                    var warp = list.get(page * 45 + i); holder.entries.put(slot, warp.id());
                    holder.inventory.setItem(slot, item(player, Material.ENDER_PEARL, warp.name(), List.of(destination(warp), text(player, "warps.select-hint"))));
                }
                if (page > 0) holder.inventory.setItem(48, button(player, Material.RED_STAINED_GLASS_PANE, "features.previous", "features.previous"));
                if ((page + 1) * 45 < list.size()) holder.inventory.setItem(50, button(player, Material.BLUE_STAINED_GLASS_PANE, "features.next", "features.next"));
                if (owner) {
                    int limit = WarpTeleportService.limit(player, plugin.getConfig().getInt("teleportation.warps.base-limit", 3));
                    holder.inventory.setItem(49, item(player, Material.LIME_DYE, text(player, "warps.create"),
                            List.of(text(player, "warps.create-hint"), list.size() + " / " + (limit < 0 ? "∞" : limit))));
                }
            }
            case WARP -> {
                var warp = lands.features(holder.land).warps().stream().filter(value -> value.id().equals(holder.warp)).findFirst().orElse(null);
                if (warp == null) { open(player, new Holder(holder.land, holder.admin, Screen.WARPS, holder.page, null)); return; }
                holder.inventory.setItem(4, item(player, Material.NAME_TAG, warp.name(), List.of(destination(warp))));
                holder.inventory.setItem(owner ? 12 : 13, button(player, Material.ENDER_PEARL, "warps.teleport", "warps.teleport-click"));
                if (owner) {
                    holder.inventory.setItem(13, button(player, Material.NAME_TAG, "warps.rename", "warps.rename-hint"));
                    holder.inventory.setItem(14, button(player, Material.HOPPER, "warps.delete", "warps.delete-hint"));
                }
            }
            case DELETE -> {
                if (!owner) return;
                holder.inventory.setItem(13, button(player, Material.RED_DYE, "warps.delete", "warps.confirm-delete"));
            }
        }
        player.openInventory(holder.inventory);
    }
    private String destination(LandWarp warp) {
        World world = Bukkit.getWorld(warp.worldId());
        return (world == null ? warp.worldId().toString() : world.getName()) + " | " + String.format(Locale.ROOT, "%.2f / %.2f / %.2f", warp.x(), warp.y(), warp.z());
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Input input = inputs.get(player.getUniqueId());
        if (input != null && event.getView().getTopInventory().equals(input.view().getTopInventory())) {
            event.setCancelled(true);
            if (event.getRawSlot() == 2) submit(player, input);
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true); int slot = event.getRawSlot();
        if (slot < 0 || slot >= holder.inventory.getSize()) return;
        if (!authorized(player, holder.land, holder.admin)) { player.closeInventory(); return; }
        Bukkit.getScheduler().runTask(plugin, () -> {
            try { handle(player, holder, slot); }
            catch (io.github.devkruidenier.landward.storage.StorageException exception) { tell(player, "general.storage-error"); }
            catch (IllegalArgumentException | NoSuchElementException exception) { tell(player, "warps.invalid"); }
        });
    }
    private void handle(Player player, Holder holder, int slot) throws io.github.devkruidenier.landward.storage.StorageException {
        if (!authorized(player, holder.land, holder.admin)) return;
        if (slot == holder.inventory.getSize() - 1) { player.closeInventory(); return; }
        if (slot == holder.inventory.getSize() - 9) {
            if (holder.screen == Screen.WARPS) {
                if (holder.admin) new ClaimAdminGui(plugin, lands).openPlayerClaimDetails(player, holder.land);
                else if (manages(player, holder)) new LandManagementGui(plugin, lands).openMain(player);
                else new LandManagementGui(plugin, lands).openMemberMain(player, lands.land(holder.land).orElseThrow());
                return;
            }
            if (holder.screen == Screen.CATEGORIES) {
                if (holder.admin) new ClaimAdminGui(plugin, lands).openMain(player); else new LandManagementGui(plugin, lands).openMain(player);
            } else open(player, new Holder(holder.land, holder.admin, holder.screen == Screen.WARP || holder.screen == Screen.DELETE ? Screen.WARPS : Screen.CATEGORIES, holder.page, null));
            return;
        }
        switch (holder.screen) {
            case CATEGORIES -> {
                if (slot == 12) {
                    if (holder.admin && lands.isAdministrative(lands.land(holder.land).orElseThrow())) new ClaimAdminGui(plugin, lands).openAdminSettings(player);
                    else if (holder.admin) new ClaimAdminGui(plugin, lands).openPlayerClaimMembers(player, holder.land);
                    else if (manages(player, holder)) new LandManagementGui(plugin, lands).openSettings(player);
                } else if (slot == 13 && manages(player, holder)) open(player, new Holder(holder.land, holder.admin, Screen.ENVIRONMENT, 0, null));
            }
            case ENVIRONMENT -> {
                if (!manages(player, holder)) return;
                for (int i = 0; i < EnvironmentSettings.Rule.values().length; i++) if (slot == FeatureMenuLayout.environmentSlot(i)) {
                    var rule = EnvironmentSettings.Rule.values()[i]; var settings = lands.features(holder.land).environment();
                    lands.environment(holder.land, settings.with(rule, !settings.allows(rule))); open(player, holder); return;
                }
            }
            case WARPS -> {
                if (slot == 48 && holder.page > 0) open(player, new Holder(holder.land, holder.admin, Screen.WARPS, holder.page - 1, null));
                else if (slot == 50 && lands.features(holder.land).warps().size() > (holder.page + 1) * 45) open(player, new Holder(holder.land, holder.admin, Screen.WARPS, holder.page + 1, null));
                else if (slot == 49 && !holder.admin && manages(player, holder)) input(player, holder.land, null);
                else if (holder.entries.containsKey(slot)) open(player, new Holder(holder.land, holder.admin, Screen.WARP, holder.page, holder.entries.get(slot)));
            }
            case WARP -> {
                boolean owner = !holder.admin && manages(player, holder);
                if (!destinationWorldEnabled(holder.land, holder.warp)) { tell(player, "warps.unavailable"); return; }
                if (slot == (owner ? 12 : 13)) { player.closeInventory(); teleports.begin(player, holder.land, holder.warp, holder.admin); }
                else if (slot == 13 && !holder.admin && manages(player, holder)) input(player, holder.land, holder.warp);
                else if (slot == 14 && !holder.admin && manages(player, holder)) open(player, new Holder(holder.land, false, Screen.DELETE, holder.page, holder.warp));
            }
            case DELETE -> {
                if (!destinationWorldEnabled(holder.land, holder.warp)) { tell(player, "warps.unavailable"); return; }
                if (slot == 13 && !holder.admin && manages(player, holder)) {
                    lands.deleteWarp(player.getUniqueId(), holder.land, holder.warp);
                    open(player, new Holder(holder.land, false, Screen.WARPS, holder.page, null));
                }
            }
        }
    }
    private void input(Player player, UUID land, UUID warp) {
        player.closeInventory(); var view = NativeAnvilInput.create(player, text(player, warp == null ? "warps.create" : "warps.rename"));
        String seed = warp == null ? " " : lands.features(land).warps().stream().filter(value -> value.id().equals(warp)).findFirst().orElseThrow().name();
        view.getTopInventory().setItem(0, item(player, Material.NAME_TAG, seed, List.of()));
        inputs.put(player.getUniqueId(), new Input(land, warp, view)); player.openInventory(view);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void prepare(PrepareAnvilEvent event) {
        Input input = inputs.get(event.getView().getPlayer().getUniqueId());
        if (input == null || !input.view().getTopInventory().equals(event.getView().getTopInventory())) return;
        String name = AnvilTextInput.capture(input.view().getRenameText());
        event.setResult(name == null ? null : item((Player)event.getView().getPlayer(), Material.NAME_TAG, name, List.of(text((Player)event.getView().getPlayer(), "gui.anvil-confirm"))));
        input.view().setRepairCost(0); input.view().setMaximumRepairCost(Integer.MAX_VALUE);
    }
    private void submit(Player player, Input input) {
        String name = AnvilTextInput.capture(input.view().getRenameText()); if (name == null) return;
        inputs.remove(player.getUniqueId()); input.view().getTopInventory().clear();
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            if (!authorized(player, input.land(), false)) return;
            if (input.warp() != null && !destinationWorldEnabled(input.land(), input.warp())) { tell(player, "warps.unavailable"); return; }
            try {
                if (input.warp() == null) {
                    Location location = player.getLocation();
                    lands.addWarp(player.getUniqueId(), input.land(), new LandWarp(UUID.randomUUID(), name, location.getWorld().getUID(),
                            location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch()),
                            WarpTeleportService.limit(player, plugin.getConfig().getInt("teleportation.warps.base-limit", 3)));
                } else lands.renameWarp(player.getUniqueId(), input.land(), input.warp(), name);
            } catch (IllegalArgumentException exception) { tell(player, "warps.invalid"); }
            catch (io.github.devkruidenier.landward.storage.StorageException exception) { tell(player, "general.storage-error"); }
            open(player, new Holder(input.land(), false, Screen.WARPS, 0, null));
        });
    }
    @EventHandler public void close(InventoryCloseEvent event) {
        Input input = inputs.get(event.getPlayer().getUniqueId());
        if (input != null && input.view().getTopInventory().equals(event.getView().getTopInventory())) { input.view().getTopInventory().clear(); inputs.remove(event.getPlayer().getUniqueId()); }
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder || inputs.containsKey(event.getWhoClicked().getUniqueId())) event.setCancelled(true);
    }
    private ItemStack button(Player player, Material material, String key, String hint) { return item(player, material, text(player, key), List.of(text(player, hint))); }
    private boolean destinationWorldEnabled(UUID land, UUID warp) {
        return lands.features(land).warps().stream().filter(value -> value.id().equals(warp)).findFirst()
                .map(value -> Bukkit.getWorld(value.worldId())).filter(Objects::nonNull).map(plugin::isLandWorld).orElse(false);
    }
    private ItemStack item(Player player, Material material, String label, List<String> lore) {
        var item = new ItemStack(material); var meta = item.getItemMeta(); meta.setDisplayName(label); meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES); item.setItemMeta(meta); return item;
    }
    private String text(Player player, String key) { return plugin.localeService().message(plugin.localeService().localeFor(player), key); }
    private void tell(Player player, String key) { player.sendMessage(text(player, key)); }
}
