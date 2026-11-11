package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.Land;
import io.github.devkruidenier.landward.model.LandMember;
import io.github.devkruidenier.landward.model.MemberPermissions;
import io.github.devkruidenier.landward.model.LandProtectionSettings;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-game member management: select members, toggle rights and remove them. */
public final class LandManagementGui {
    private static final int LARGE_SIZE = 54;
    private static final int COMPACT_SIZE = 27;
    private final LandWardPlugin plugin;
    private final LandManager landManager;

    public LandManagementGui(LandWardPlugin plugin, LandManager landManager) { this.plugin = plugin; this.landManager = landManager; }

    public void openMain(Player player) {
        Optional<Land> optionalLand = landManager.ownedBy(player.getUniqueId());
        if (optionalLand.isEmpty()) {
            // Membership never blocks a player from creating their own one land.
            // The welcome screen also exposes every land where they are trusted.
            openWelcome(player);
            return;
        }
        Land land = optionalLand.get();
        LandManagementHolder holder = holder(LandManagementHolder.Screen.MAIN, null, message(player, "gui.management-title", Map.of("name", land.name())), COMPACT_SIZE);
        Inventory inventory = holder.getInventory();
        inventory.setItem(4, item(Material.WRITABLE_BOOK, land.name(), NamedTextColor.GOLD, List.of(
                message(player, "gui.management-claimed-chunks", Map.of("count", landManager.claimCount(land.id()))),
                message(player, "gui.management-owner", Map.of("name", player.getName()))
        )));
        inventory.setItem(11, item(Material.FILLED_MAP, message(player, "gui.management-chunk-map"), NamedTextColor.AQUA, List.of(message(player, "gui.management-open-map"))));
        inventory.setItem(13, item(Material.ENDER_PEARL, message(player, "gui.management-teleport"), NamedTextColor.AQUA, List.of(message(player, "gui.management-teleport-hint"))));
        inventory.setItem(15, item(Material.COMPARATOR, message(player, "gui.management-settings"), NamedTextColor.YELLOW, List.of(message(player, "gui.management-settings-hint"))));
        inventory.setItem(17, item(Material.HOPPER, message(player, "gui.management-delete-land"), NamedTextColor.RED, List.of(message(player, "gui.management-delete-land-hint"))));
        inventory.setItem(26, item(Material.ARROW, message(player, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        GuiLayout.open(player, inventory, plugin);
    }

    /** Read-only land overview for trusted members; ownership controls are deliberately absent. */
    void openMemberMain(Player player, Land land) {
        LandManagementHolder holder = holder(LandManagementHolder.Screen.MEMBER_MAIN, null,
                message(player, "gui.management-title", Map.of("name", land.name())), COMPACT_SIZE);
        holder.selectedLandId(land.id());
        Inventory inventory = holder.getInventory();
        inventory.setItem(4, item(Material.WRITABLE_BOOK, land.name(), NamedTextColor.GOLD, List.of(
                message(player, "gui.status-member"),
                message(player, "gui.management-claimed-chunks", Map.of("count", landManager.claimCount(land.id()))),
                message(player, "gui.management-owner", Map.of("name", name(player, land.ownerId())))
        )));
        inventory.setItem(26, item(Material.ARROW, message(player, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        GuiLayout.open(player, inventory, plugin);
    }

    void openMemberLands(Player player, List<Land> lands) {
        LandManagementHolder holder = holder(LandManagementHolder.Screen.MEMBER_LANDS, null, message(player, "gui.management-members"), LARGE_SIZE);
        Inventory inventory = holder.getInventory();
        GuiLayout.Entries entries = new GuiLayout.Entries();
        int slot = 0;
        for (Land land : lands) {


            entries.add(() -> item(Material.WRITABLE_BOOK, land.name(), NamedTextColor.GOLD, List.of(
                    message(player, "gui.management-owner", Map.of("name", name(player, land.ownerId()))),
                    message(player, "gui.management-claimed-chunks", Map.of("count", landManager.claimCount(land.id()))),
                    message(player, "gui.hint-select-land")
            )), key -> holder.playersBySlot().put(key, land.id()));
            slot++;
        }
        inventory.setItem(53, item(Material.ARROW, message(player, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        entries.open(player, inventory, plugin);
    }

    public void openSettings(Player player) {
        Optional<Land> optionalLand = landManager.ownedBy(player.getUniqueId());
        if (optionalLand.isEmpty()) { openWelcome(player); return; }
        Land land = optionalLand.get();
        LandProtectionSettings settings = land.protectionSettings();
        MemberPermissions permissions = land.memberPermissions();
        LandManagementHolder holder = holder(LandManagementHolder.Screen.SETTINGS, null, message(player, "gui.management-title", Map.of("name", land.name())), LARGE_SIZE);
        Inventory inventory = holder.getInventory();
        // Row 2: combat and mob rules.
        inventory.setItem(10, permissionItem(player, Material.IRON_SWORD, message(player, "gui.admin-pvp"), settings.memberPvp()));
        inventory.setItem(11, permissionItem(player, Material.ZOMBIE_HEAD, message(player, "gui.admin-hostile-mob-kills"), settings.hostileMobKills()));
        inventory.setItem(12, permissionItem(player, Material.BEEF, message(player, "gui.admin-passive-mob-kills"), settings.passiveMobKills()));
        inventory.setItem(13, permissionItem(player, Material.CREEPER_SPAWN_EGG, message(player, "gui.admin-hostile-mob-spawns"), settings.hostileMobSpawns()));
        // Row 3: access granted to every trusted member.
        inventory.setItem(19, permissionItem(player, Material.IRON_PICKAXE, message(player, "gui.management-build"), permissions.build()));
        inventory.setItem(20, permissionItem(player, Material.CHEST, message(player, "gui.management-containers"), permissions.containers()));
        inventory.setItem(21, permissionItem(player, Material.LEAD, message(player, "gui.management-entity-interactions"), permissions.entities()));
        inventory.setItem(23, permissionItem(player, Material.OAK_DOOR, message(player, "gui.admin-doors"), settings.doors()));
        inventory.setItem(24, permissionItem(player, Material.OAK_SIGN, message(player, "gui.admin-interactions"), settings.interactions()));
        // Row 5: membership and land administration.
        inventory.setItem(37, item(Material.LIME_DYE, message(player, "gui.management-add-member"), NamedTextColor.GREEN, List.of(message(player, "gui.management-choose-online"))));
        inventory.setItem(40, item(Material.PLAYER_HEAD, message(player, "gui.management-members"), NamedTextColor.AQUA, List.of(message(player, "gui.management-manage-rights"))));
        inventory.setItem(43, item(Material.NAME_TAG, message(player, "gui.management-rename"), NamedTextColor.YELLOW, List.of(message(player, "gui.management-rename-hint"))));
        inventory.setItem(53, item(Material.ARROW, message(player, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        GuiLayout.open(player, inventory, plugin);
    }

    public void openLandDeleteConfirmation(Player player) {
        Optional<Land> optionalLand = landManager.ownedBy(player.getUniqueId());
        if (optionalLand.isEmpty()) { openWelcome(player); return; }
        Land land = optionalLand.get();
        LandManagementHolder holder = holder(LandManagementHolder.Screen.LAND_DELETE_CONFIRM, null,
                message(player, "gui.management-delete-land-title"), COMPACT_SIZE);
        Inventory inventory = holder.getInventory();
        inventory.setItem(11, item(Material.HOPPER, message(player, "gui.management-delete-land-confirm"), NamedTextColor.GREEN,
                List.of(message(player, "gui.management-delete-land-warning", Map.of("name", land.name())))));
        inventory.setItem(15, item(Material.RED_DYE, message(player, "gui.cancel"), NamedTextColor.RED, List.of()));
        inventory.setItem(26, item(Material.ARROW, message(player, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        GuiLayout.open(player, inventory, plugin);
    }

    public boolean teleportToOwnedLand(Player player) {
        return landManager.ownedBy(player.getUniqueId()).flatMap(land -> landManager.claims().stream()
                .filter(claim -> claim.landId().equals(land.id())).findFirst()).map(claim -> {
            World world = Bukkit.getWorld(claim.position().worldId());
            if (world == null) return false;
            int x = claim.position().x() * 16 + 8;
            int z = claim.position().z() * 16 + 8;
            int delaySeconds = Math.max(0, plugin.getConfig().getInt("teleportation.player-land-delay-seconds", 2));
            String locale = plugin.localeService().localeFor(player);
            if (delaySeconds == 0) {
                player.teleport(new Location(world, x + 0.5D, world.getHighestBlockYAt(x, z) + 1, z + 0.5D));
                player.sendMessage(message(player, "gui.management-teleport-complete"));
                return true;
            }
            player.sendMessage(message(player, "gui.management-teleport-started", Map.of("seconds", delaySeconds)));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.teleport(new Location(world, x + 0.5D, world.getHighestBlockYAt(x, z) + 1, z + 0.5D));
                player.sendMessage(plugin.localeService().message(locale, "gui.management-teleport-complete"));
            }, delaySeconds * 20L);
            return true;
        }).orElse(false);
    }

    public void openWelcome(Player player) {
        LandManagementHolder holder = holder(LandManagementHolder.Screen.WELCOME, null, message(player, "gui.welcome-title"), COMPACT_SIZE);
        Inventory inventory = holder.getInventory();
        inventory.setItem(13, item(Material.GRASS_BLOCK, message(player, "gui.welcome-create"), NamedTextColor.GREEN, List.of(
                message(player, "gui.welcome-create-hint"),
                message(player, "gui.welcome-create-details")
        )));
        inventory.setItem(22, item(Material.FILLED_MAP, message(player, "gui.management-chunk-map"), NamedTextColor.AQUA, List.of(message(player, "gui.management-open-map"))));
        List<Land> memberLands = landManager.memberLands(player.getUniqueId());
        if (!memberLands.isEmpty()) {
            inventory.setItem(15, item(Material.WRITABLE_BOOK, message(player, "gui.member-lands"), NamedTextColor.GOLD,
                    List.of(message(player, "gui.member-lands-hint"))));
        }
        GuiLayout.open(player, inventory, plugin);
    }

    public void openMembers(Player player) {
        LandManagementHolder holder = holder(LandManagementHolder.Screen.ADD_MEMBERS, null, message(player, "gui.management-add-title"), LARGE_SIZE);
        Inventory inventory = holder.getInventory();
        GuiLayout.Entries entries = new GuiLayout.Entries();
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers().stream().sorted(java.util.Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)).toList()) {
            if (target.getUniqueId().equals(player.getUniqueId()) || landManager.ownedBy(player.getUniqueId()).isEmpty()) continue;
            Land land = landManager.ownedBy(player.getUniqueId()).orElseThrow();
            if (landManager.members(land.id()).stream().anyMatch(member -> member.playerId().equals(target.getUniqueId()))) continue;


            entries.add(() -> playerHead(target.getUniqueId(), target.getName(), message(player, "gui.management-add-player")), key -> holder.playersBySlot().put(key, target.getUniqueId()));
            slot++;
        }
        inventory.setItem(53, item(Material.ARROW, message(player, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        entries.open(player, inventory, plugin);
    }

    public void openMemberList(Player player) {
        Optional<Land> optionalLand = landManager.ownedBy(player.getUniqueId());
        if (optionalLand.isEmpty()) return;
        LandManagementHolder holder = holder(LandManagementHolder.Screen.MEMBERS, null, message(player, "gui.management-members"), LARGE_SIZE);
        Inventory inventory = holder.getInventory();
        GuiLayout.Entries entries = new GuiLayout.Entries();
        int slot = 0;
        for (LandMember member : landManager.members(optionalLand.get().id())) {


            entries.add(() -> playerHead(member.playerId(), name(player, member.playerId()), message(player, "gui.management-manage-rights")), key -> holder.playersBySlot().put(key, member.playerId()));
            slot++;
        }
        if (slot == 0) inventory.setItem(22, item(Material.GRAY_DYE, message(player, "gui.management-no-members"), NamedTextColor.GRAY, List.of(message(player, "gui.management-add-member-hint"))));
        inventory.setItem(53, item(Material.ARROW, message(player, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        entries.open(player, inventory, plugin);
    }

    public void openDefaultRights(Player player) {
        Optional<Land> optionalLand = landManager.ownedBy(player.getUniqueId());
        if (optionalLand.isEmpty()) return;
        MemberPermissions permissions = optionalLand.get().memberPermissions();
        LandManagementHolder holder = holder(LandManagementHolder.Screen.DEFAULT_RIGHTS, null, message(player, "gui.management-rights-title"), COMPACT_SIZE);
        Inventory inventory = holder.getInventory();
        inventory.setItem(11, permissionItem(player, Material.IRON_PICKAXE, message(player, "gui.management-build"), permissions.build()));
        inventory.setItem(13, permissionItem(player, Material.CHEST, message(player, "gui.management-containers"), permissions.containers()));
        inventory.setItem(15, permissionItem(player, Material.LEAD, message(player, "gui.management-entities"), permissions.entities()));
        inventory.setItem(26, item(Material.ARROW, message(player, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        GuiLayout.open(player, inventory, plugin);
    }

    public void openMemberActions(Player player, UUID memberId) {
        LandManagementHolder holder = holder(LandManagementHolder.Screen.MEMBER, memberId, message(player, "gui.management-members"), COMPACT_SIZE);
        Inventory inventory = holder.getInventory();
        MemberPermissions permissions = landManager.ownedBy(player.getUniqueId())
                .flatMap(land -> landManager.members(land.id()).stream().filter(member -> member.playerId().equals(memberId)).findFirst())
                .map(LandMember::permissions).orElse(MemberPermissions.NONE);
        inventory.setItem(4, playerHead(memberId, name(player, memberId), message(player, "gui.management-configure-member")));
        inventory.setItem(10, permissionItem(player, Material.IRON_PICKAXE, message(player, "gui.management-build"), permissions.build(), "member"));
        inventory.setItem(13, permissionItem(player, Material.CHEST, message(player, "gui.management-containers"), permissions.containers(), "member"));
        inventory.setItem(16, permissionItem(player, Material.LEAD, message(player, "gui.management-entities"), permissions.entities(), "member"));
        inventory.setItem(22, item(Material.HOPPER, message(player, "gui.management-remove-member"), NamedTextColor.RED, List.of(message(player, "gui.management-remove-hint"))));
        inventory.setItem(26, item(Material.ARROW, message(player, "gui.management-back"), NamedTextColor.YELLOW, List.of()));
        GuiLayout.open(player, inventory, plugin);
    }

    private LandManagementHolder holder(LandManagementHolder.Screen screen, UUID member, String title, int size) {
        LandManagementHolder holder = new LandManagementHolder(screen, member);
        // Inventory headers sit on Minecraft's light GUI background. Dark aqua keeps
        // LandWard's teal identity while retaining strong contrast.
        Inventory inventory = GuiText.inventory(holder, size, title);
        holder.inventory(inventory);
        return holder;
    }
    private ItemStack permissionItem(Player player, Material material, String label, boolean enabled) {
        return permissionItem(player, material, label, enabled,
                material == Material.IRON_PICKAXE || material == Material.CHEST || material == Material.LEAD ? "members" : null);
    }
    private ItemStack permissionItem(Player player, Material material, String label, boolean enabled, String scope) {
        if (material == Material.IRON_SWORD && !player.getWorld().getPVP()) {
            return item(material, label + ": " + message(player, "gui.management-unavailable"), NamedTextColor.GRAY,
                    GuiHints.pvpUnavailable(key -> message(player, key)));
        }
        return item(material, label + ": " + message(player, enabled ? "gui.management-allowed" : "gui.management-denied"),
                enabled ? NamedTextColor.GREEN : NamedTextColor.RED,
                GuiHints.setting(material.name(), false, scope, key -> message(player, key)));
    }
    private ItemStack playerHead(UUID id, String label, String lore) { ItemStack item = item(Material.PLAYER_HEAD, label, NamedTextColor.AQUA, List.of(lore)); SkullMeta meta = (SkullMeta) item.getItemMeta(); meta.setOwningPlayer(Bukkit.getOfflinePlayer(id)); item.setItemMeta(meta); return item; }
    private ItemStack item(Material material, String label, NamedTextColor color, List<String> lore) { return GuiText.item(material, label, color, lore); }
    /** Never expose a raw UUID in a player-facing menu when a profile is unavailable. */
    private String name(Player viewer, UUID id) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(id);
        return player.getName() == null ? message(viewer, "gui.unknown-player") : player.getName();
    }
    private String message(Player player, String key) { return plugin.localeService().message(plugin.localeService().localeFor(player), key); }
    private String message(Player player, String key, Map<String, ?> placeholders) { return plugin.localeService().message(plugin.localeService().localeFor(player), key, placeholders); }
}
