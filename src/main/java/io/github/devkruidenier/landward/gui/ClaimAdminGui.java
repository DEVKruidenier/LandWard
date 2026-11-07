package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.MemberPermissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Administrator-only entry point for claim moderation and world activation. */
public final class ClaimAdminGui {
    private final LandWardPlugin plugin;
    private final LandManager lands;

    public ClaimAdminGui(LandWardPlugin plugin, LandManager lands) { this.plugin = plugin; this.lands = lands; }

    public void openMain(Player player) {
        String locale = plugin.localeService().localeFor(player);
        ClaimAdminHolder holder = holder(ClaimAdminHolder.Screen.MAIN, locale, 27, "gui.admin-title");
        Inventory inventory = holder.getInventory();
        inventory.setItem(9, item(Material.FILLED_MAP, plugin.localeService().message(locale, "gui.admin-player-claims-title"), NamedTextColor.AQUA, List.of(plugin.localeService().message(locale, "gui.admin-player-claims-hint"))));
        inventory.setItem(11, item(Material.OBSIDIAN, plugin.localeService().message(locale, "gui.admin-claims-title"), NamedTextColor.LIGHT_PURPLE, List.of(plugin.localeService().message(locale, "gui.admin-claims-hint"))));
        inventory.setItem(13, item(Material.COMPARATOR, plugin.localeService().message(locale, "gui.admin-settings"), NamedTextColor.LIGHT_PURPLE, List.of(plugin.localeService().message(locale, "gui.admin-settings-hint"))));
        inventory.setItem(15, item(Material.CLOCK, plugin.localeService().message(locale, "gui.admin-reload"), NamedTextColor.YELLOW, List.of(plugin.localeService().message(locale, "gui.admin-reload-hint"))));
        inventory.setItem(17, item(Material.GRASS_BLOCK, plugin.localeService().message(locale, "gui.admin-worlds"), NamedTextColor.GREEN, List.of(plugin.localeService().message(locale, "gui.admin-worlds-hint"))));
        inventory.setItem(26, item(Material.ARROW, plugin.localeService().message(locale, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        player.openInventory(inventory);
    }

    public void openWorlds(Player player) {
        String locale = plugin.localeService().localeFor(player);
        ClaimAdminHolder holder = holder(ClaimAdminHolder.Screen.WORLDS, locale, 54, "gui.admin-worlds-title");
        Inventory inventory = holder.getInventory();
        int slot = 0;
        for (World world : Bukkit.getWorlds()) {
            if (slot >= 45) break;
            boolean active = plugin.isLandWorld(world);
            holder.worldsBySlot().put(slot, world.getName());
            inventory.setItem(slot++, item(active ? Material.LIME_DYE : Material.GRAY_DYE,
                    plugin.localeService().message(locale, active ? "gui.admin-world-enabled" : "gui.admin-world-disabled"),
                    active ? NamedTextColor.GREEN : NamedTextColor.RED, List.of(
                    world.getName(),
                    plugin.localeService().message(locale, active ? "gui.admin-world-disable-hint" : "gui.admin-world-enable-hint")
            )));
        }
        inventory.setItem(53, item(Material.ARROW, plugin.localeService().message(locale, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        player.openInventory(inventory);
    }

    /** Lists every player-owned land; server-owned safe zones stay in their separate map. */
    public void openPlayerClaims(Player player) {
        String locale = plugin.localeService().localeFor(player);
        ClaimAdminHolder holder = holder(ClaimAdminHolder.Screen.PLAYER_CLAIMS, locale, 54, "gui.admin-player-claims-title");
        Inventory inventory = holder.getInventory();
        int slot = 0;
        for (var land : lands.playerLands()) {
            if (slot >= 45) break;
            holder.landsBySlot().put(slot, land.id());
            // A player head can expose an unrelated profile name for an old or
            // manually created UUID. A neutral land icon keeps this list clear.
            inventory.setItem(slot++, item(Material.GRASS_BLOCK, land.name(), NamedTextColor.AQUA,
                    List.of(plugin.localeService().message(locale, "gui.admin-player-claim-select", java.util.Map.of(
                            "owner", name(locale, land.ownerId()), "count", lands.claimCount(land.id())
                    )))));
        }
        if (slot == 0) inventory.setItem(22, item(Material.GRAY_DYE, plugin.localeService().message(locale, "gui.admin-no-player-claims"), NamedTextColor.GRAY, List.of()));
        inventory.setItem(53, item(Material.ARROW, plugin.localeService().message(locale, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        player.openInventory(inventory);
    }

    public void openPlayerClaimDetails(Player player, UUID landId) {
        var land = lands.land(landId).filter(value -> !lands.isAdministrative(value));
        if (land.isEmpty()) { openPlayerClaims(player); return; }
        String locale = plugin.localeService().localeFor(player);
        ClaimAdminHolder holder = holder(ClaimAdminHolder.Screen.PLAYER_CLAIM_DETAILS, locale, 27, "gui.admin-player-claim-details-title");
        holder.selectedLandId(landId);
        Inventory inventory = holder.getInventory();
        var value = land.get();
        inventory.setItem(10, item(Material.ENDER_PEARL, plugin.localeService().message(locale, "gui.admin-teleport"), NamedTextColor.AQUA,
                List.of(plugin.localeService().message(locale, "gui.admin-teleport-hint"))));
        inventory.setItem(12, playerHead(value.ownerId(), plugin.localeService().message(locale, "gui.admin-change-owner"),
                plugin.localeService().message(locale, "gui.admin-change-owner-hint", java.util.Map.of("owner", name(locale, value.ownerId())))));
        inventory.setItem(14, item(Material.PAPER, plugin.localeService().message(locale, "gui.admin-claim-statistics"), NamedTextColor.GOLD,
                List.of(plugin.localeService().message(locale, "gui.management-claimed-chunks", java.util.Map.of("count", lands.claimCount(landId))),
                        plugin.localeService().message(locale, "gui.management-owner", java.util.Map.of("name", name(locale, value.ownerId()))))));
        inventory.setItem(16, item(Material.PLAYER_HEAD, plugin.localeService().message(locale, "gui.management-members"), NamedTextColor.AQUA,
                List.of(plugin.localeService().message(locale, "gui.admin-claim-members-hint", java.util.Map.of("count", lands.members(landId).size())))));
        inventory.setItem(26, item(Material.ARROW, plugin.localeService().message(locale, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        player.openInventory(inventory);
    }

    public void openPlayerClaimMembers(Player player, UUID landId) {
        var land = lands.land(landId).filter(value -> !lands.isAdministrative(value));
        if (land.isEmpty()) { openPlayerClaims(player); return; }
        String locale = plugin.localeService().localeFor(player);
        ClaimAdminHolder holder = holder(ClaimAdminHolder.Screen.PLAYER_CLAIM_MEMBERS, locale, 54, "gui.management-members");
        holder.selectedLandId(landId);
        Inventory inventory = holder.getInventory();
        int slot = 0;
        for (var member : lands.members(landId)) {
            if (slot >= 45) break;
            inventory.setItem(slot++, playerHead(member.playerId(), name(locale, member.playerId()), plugin.localeService().message(locale, "gui.admin-member-permissions", java.util.Map.of("permissions", member.permissions().toString()))));
        }
        if (slot == 0) inventory.setItem(22, item(Material.GRAY_DYE, plugin.localeService().message(locale, "gui.management-no-members"), NamedTextColor.GRAY, List.of()));
        inventory.setItem(53, item(Material.ARROW, plugin.localeService().message(locale, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        player.openInventory(inventory);
    }

    public boolean teleportToClaim(Player player, UUID landId) {
        return lands.claims().stream().filter(claim -> claim.landId().equals(landId)).findFirst().map(claim -> {
            World world = Bukkit.getWorld(claim.position().worldId());
            if (world == null) return false;
            int x = claim.position().x() * 16 + 8;
            int z = claim.position().z() * 16 + 8;
            player.teleport(new Location(world, x + 0.5D, world.getHighestBlockYAt(x, z) + 1, z + 0.5D));
            return true;
        }).orElse(false);
    }

    public void openAdminSettings(Player player) {
        String locale = plugin.localeService().localeFor(player);
        ClaimAdminHolder holder = holder(ClaimAdminHolder.Screen.ADMIN_SETTINGS, locale, 54, "gui.admin-settings");
        Inventory inventory = holder.getInventory();
        setting(inventory, 10, Material.IRON_SWORD, locale, "gui.admin-pvp", "protection.allow-pvp");
        setting(inventory, 11, Material.ZOMBIE_HEAD, locale, "gui.admin-hostile-mob-kills", "protection.allow-hostile-mob-kills");
        setting(inventory, 12, Material.BEEF, locale, "gui.admin-passive-mob-kills", "protection.allow-passive-mob-kills");
        setting(inventory, 13, Material.CREEPER_SPAWN_EGG, locale, "gui.admin-hostile-mob-spawns", "protection.allow-hostile-mob-spawns");
        setting(inventory, 19, Material.CHEST, locale, "gui.management-containers", "protection.allow-containers");
        setting(inventory, 20, Material.OAK_DOOR, locale, "gui.admin-doors", "protection.allow-doors");
        setting(inventory, 21, Material.OAK_SIGN, locale, "gui.admin-interactions", "protection.allow-interactions");
        inventory.setItem(37, item(Material.LIME_DYE, plugin.localeService().message(locale, "gui.management-add-member"), NamedTextColor.GREEN, List.of(plugin.localeService().message(locale, "gui.management-choose-online"))));
        inventory.setItem(40, item(Material.PLAYER_HEAD, plugin.localeService().message(locale, "gui.management-members"), NamedTextColor.AQUA, List.of(plugin.localeService().message(locale, "gui.admin-members-hint"))));
        inventory.setItem(53, item(Material.ARROW, plugin.localeService().message(locale, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        player.openInventory(inventory);
    }

    public void openAdminMemberChoices(Player player) {
        String locale = plugin.localeService().localeFor(player);
        ClaimAdminHolder holder = holder(ClaimAdminHolder.Screen.ADMIN_ADD_MEMBERS, locale, 54, "gui.management-add-title");
        Inventory inventory = holder.getInventory();
        Optional<io.github.devkruidenier.landward.model.Land> land = lands.administrativeLand();
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (slot >= 45 || land.isEmpty() || lands.permissions(target.getUniqueId(), land.get().id()).equals(io.github.devkruidenier.landward.model.MemberPermissions.FULL)) continue;
            holder.playersBySlot().put(slot, target.getUniqueId());
            inventory.setItem(slot++, playerHead(target.getUniqueId(), target.getName(), plugin.localeService().message(locale, "gui.management-add-player")));
        }
        inventory.setItem(53, item(Material.ARROW, plugin.localeService().message(locale, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        player.openInventory(inventory);
    }

    public void openAdminMemberList(Player player) {
        String locale = plugin.localeService().localeFor(player);
        ClaimAdminHolder holder = holder(ClaimAdminHolder.Screen.ADMIN_MEMBERS, locale, 54, "gui.management-members");
        Inventory inventory = holder.getInventory();
        int slot = 0;
        for (var member : lands.administrativeLand().map(value -> lands.members(value.id())).orElse(List.of())) {
            if (slot >= 45) break;
            holder.playersBySlot().put(slot, member.playerId());
            inventory.setItem(slot++, playerHead(member.playerId(), name(locale, member.playerId()), plugin.localeService().message(locale, "gui.admin-member-remove-hint")));
        }
        if (slot == 0) inventory.setItem(22, item(Material.GRAY_DYE, plugin.localeService().message(locale, "gui.management-no-members"), NamedTextColor.GRAY, List.of()));
        inventory.setItem(53, item(Material.ARROW, plugin.localeService().message(locale, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        player.openInventory(inventory);
    }

    /** Lets an administrator change the access of one trusted Safe Zone member. */
    public void openAdminMemberActions(Player player, UUID memberId) {
        String locale = plugin.localeService().localeFor(player);
        var land = lands.administrativeLand();
        if (land.isEmpty()) { openAdminMemberList(player); return; }
        MemberPermissions permissions = lands.members(land.get().id()).stream()
                .filter(member -> member.playerId().equals(memberId)).findFirst()
                .map(io.github.devkruidenier.landward.model.LandMember::permissions)
                .orElse(MemberPermissions.NONE);
        ClaimAdminHolder holder = holder(ClaimAdminHolder.Screen.ADMIN_MEMBER, locale, 27, "gui.management-members");
        holder.selectedMemberId(memberId);
        Inventory inventory = holder.getInventory();
        inventory.setItem(4, playerHead(memberId, name(locale, memberId), plugin.localeService().message(locale, "gui.management-configure-member")));
        inventory.setItem(10, permissionItem(locale, Material.IRON_PICKAXE, "gui.management-build", permissions.build()));
        inventory.setItem(13, permissionItem(locale, Material.CHEST, "gui.management-containers", permissions.containers()));
        inventory.setItem(16, permissionItem(locale, Material.LEAD, "gui.management-entities", permissions.entities()));
        inventory.setItem(22, item(Material.BARRIER, plugin.localeService().message(locale, "gui.management-remove-member"), NamedTextColor.RED,
                List.of(plugin.localeService().message(locale, "gui.management-remove-hint"))));
        inventory.setItem(26, item(Material.ARROW, plugin.localeService().message(locale, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        player.openInventory(inventory);
    }

    private ClaimAdminHolder holder(ClaimAdminHolder.Screen screen, String locale, int size, String titleKey) {
        ClaimAdminHolder holder = new ClaimAdminHolder(screen);
        Inventory inventory = Bukkit.createInventory(holder, size, Component.text(plugin.localeService().message(locale, titleKey), NamedTextColor.DARK_AQUA));
        holder.inventory(inventory);
        return holder;
    }

    private ItemStack item(Material material, String label, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private void setting(Inventory inventory, int slot, Material material, String locale, String titleKey, String configKey) {
        boolean enabled = plugin.getConfig().getBoolean("admin-claims." + configKey, false);
        inventory.setItem(slot, item(material,
                plugin.localeService().message(locale, titleKey) + ": " + plugin.localeService().message(locale, enabled ? "gui.management-allowed" : "gui.management-denied"),
                enabled ? NamedTextColor.GREEN : NamedTextColor.RED,
                List.of(plugin.localeService().message(locale, "gui.management-toggle"))));
    }

    private ItemStack permissionItem(String locale, Material material, String titleKey, boolean enabled) {
        return item(material,
                plugin.localeService().message(locale, titleKey) + ": " + plugin.localeService().message(locale, enabled ? "gui.management-allowed" : "gui.management-denied"),
                enabled ? NamedTextColor.GREEN : NamedTextColor.RED,
                List.of(plugin.localeService().message(locale, "gui.management-toggle")));
    }

    private ItemStack playerHead(UUID id, String label, String lore) {
        ItemStack item = item(Material.PLAYER_HEAD, label, NamedTextColor.AQUA, List.of(lore));
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(id));
        item.setItemMeta(meta);
        return item;
    }

    /** Uses a localized fallback instead of exposing an unreadable UUID. */
    private String name(String locale, UUID id) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(id);
        return player.getName() == null ? plugin.localeService().message(locale, "gui.unknown-player") : player.getName();
    }
}
