package io.github.devkruidenier.landward.command;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.gui.LandMapGui;
import io.github.devkruidenier.landward.gui.LandManagementGui;
import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.model.Land;
import io.github.devkruidenier.landward.model.MemberPermissions;
import io.github.devkruidenier.landward.storage.StorageException;
import io.github.devkruidenier.landward.economy.ExperiencePayments;
import io.github.devkruidenier.landward.economy.VaultPayments;
import io.github.devkruidenier.landward.integration.WorldGuardIntegration;
import io.github.devkruidenier.landward.integration.VanillaSpawnProtectionIntegration;
import io.github.devkruidenier.landward.update.UpdateChecker;
import org.bukkit.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class LandCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT_COMPLETIONS = List.of("help", "info", "menu", "bypass", "update");

    private final LandWardPlugin plugin;
    private final LandManager landManager;
    private final WorldGuardIntegration worldGuard;
    private final VanillaSpawnProtectionIntegration vanillaSpawnProtection;

    public LandCommand(LandWardPlugin plugin, LandManager landManager) {
        this.plugin = plugin;
        this.landManager = landManager;
        this.worldGuard = new WorldGuardIntegration(plugin);
        this.vanillaSpawnProtection = new VanillaSpawnProtectionIntegration();
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] arguments
    ) {
        if (arguments.length == 1 && arguments[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        if (arguments.length == 1 && arguments[0].equalsIgnoreCase("update")) {
            stageUpdate(sender);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(message(sender, "general.player-only"));
            return true;
        }

        if (arguments.length == 0) {
            menu(player);
            return true;
        }
        return switch (arguments[0].toLowerCase(Locale.ROOT)) {
            // The map remains reachable for players who already know the old route.
            case "claim", "list" -> list(player);
            // Typed mutations deliberately no longer bypass the GUI confirmation flow.
            case "info" -> info(player);
            case "create", "unclaim", "rename", "add", "remove", "rights" -> menu(player);
            case "menu" -> menu(player);
            case "bypass" -> adminBypass(player);
            default -> {
                sender.sendMessage(message(sender, "general.unknown-command"));
                yield true;
            }
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] arguments
    ) {
        if (arguments.length != 1) {
            return List.of();
        }

        String prefix = arguments[0].toLowerCase(Locale.ROOT);
        return ROOT_COMPLETIONS.stream()
                .filter(completion -> !completion.equals("bypass") || sender.hasPermission("landward.bypass"))
                .filter(completion -> !completion.equals("update") || sender.hasPermission("landward.admin"))
                .filter(completion -> completion.startsWith(prefix))
                .toList();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(message(sender, "command.header", Map.of(
                "version", plugin.getDescription().getVersion()
        )));
        sender.sendMessage(message(sender, "command.description"));
        sender.sendMessage(message(sender, "command.help"));
        sender.sendMessage(message(sender, "command.help-list"));
        sender.sendMessage(message(sender, "command.help-menu"));
        if (sender.hasPermission("landward.bypass")) {
            sender.sendMessage(message(sender, "command.help-admin-bypass"));
        }
    }

    private boolean adminBypass(Player player) {
        if (!player.hasPermission("landward.bypass")) {
            player.sendMessage(message(player, "general.no-permission"));
            return true;
        }
        boolean enabled = plugin.adminBypasses().toggle(player.getUniqueId());
        player.sendMessage(message(player, enabled ? "admin-bypass.enabled" : "admin-bypass.disabled"));
        return true;
    }

    private void stageUpdate(CommandSender sender) {
        if (!sender.hasPermission("landward.admin")) {
            sender.sendMessage(message(sender, "general.no-permission"));
            return;
        }
        sender.sendMessage(message(sender, "updates.checking"));
        plugin.updateChecker().stageLatestRelease(version -> sender.sendMessage(
                message(sender, "updates.stage-started", Map.of("version", version)))).whenComplete((result, error) -> {
            if (!plugin.isEnabled()) return;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (sender instanceof Player target && !target.isOnline()) return;
                if (error != null || result == null || !result.success()) {
                    var state = plugin.updateChecker().status();
                    if (state.state() == UpdateChecker.State.LATEST) {
                        sender.sendMessage(message(sender, "updates.up-to-date", Map.of("version", plugin.getDescription().getVersion())));
                    } else if (state.state() == UpdateChecker.State.DISABLED || state.state() == UpdateChecker.State.UNAVAILABLE || state.asset() == null) {
                        sender.sendMessage(message(sender, "updates.stage-unavailable"));
                    } else sender.sendMessage(message(sender, "updates.stage-failed", Map.of("reason", error == null && result != null ? result.detail() : "unexpected error")));
                    return;
                }
                sender.sendMessage(message(sender, "updates.stage-complete", Map.of("file", result.stagedFile().getFileName())));
            });
        });
    }

    private boolean create(Player player, String[] arguments) throws StorageException {
        if (arguments.length < 2) {
            player.sendMessage(message(player, "land.create-usage"));
            return true;
        }
        return create(player, String.join(" ", java.util.Arrays.copyOfRange(arguments, 1, arguments.length)));
    }

    public boolean createFromMenu(Player player, String name) throws StorageException {
        return create(player, name);
    }

    private boolean create(Player player, String name) throws StorageException {
        if (!worldEnabled(player)) {
            player.sendMessage(message(player, "claim.world-disabled"));
            return true;
        }
        if (plugin.getConfig().getBoolean("integrations.vanilla-spawn-protection.enabled", true)
                && vanillaSpawnProtection.blocksClaim(position(player))) {
            player.sendMessage(message(player, "claim.spawn-protected"));
            return true;
        }
        if (worldGuard.blocksClaim(player.getWorld(), position(player))) {
            player.sendMessage(message(player, "claim.worldguard-protected"));
            return true;
        }
        PaymentCharge charge = charge(player);
        if (!charge.paid()) {
            player.sendMessage(message(player, charge.messageKey(), Map.of("cost", charge.cost())));
            return true;
        }
        LandManager.CreateResult result;
        try {
            result = landManager.create(player.getUniqueId(), name, position(player));
        } catch (StorageException exception) {
            charge.refund(player);
            throw exception;
        }
        String key = switch (result.status()) {
            case CREATED -> "land.created";
            case INVALID_NAME -> "land.invalid-name";
            case ALREADY_OWNER -> "land.already-owner";
            case NAME_TAKEN -> "land.name-taken";
            case CHUNK_ALREADY_CLAIMED -> "land.chunk-already-claimed";
        };
        player.sendMessage(message(player, key, Map.of(
                "name", result.land() == null ? name : result.land().name()
        )));
        if (result.status() != LandManager.CreateStatus.CREATED) charge.refund(player);
        else {
            announceCharge(player, charge);
            plugin.syncMapIntegrations();
        }
        return true;
    }

    public boolean claimFromMenu(Player player, ChunkPosition position) throws StorageException {
        return claim(player, position);
    }

    private boolean claim(Player player, ChunkPosition position) throws StorageException {
        if (!worldEnabled(player)) {
            player.sendMessage(message(player, "claim.world-disabled"));
            return true;
        }

        if (plugin.getConfig().getBoolean("integrations.vanilla-spawn-protection.enabled", true)
                && vanillaSpawnProtection.blocksClaim(position)) {
            player.sendMessage(message(player, "claim.spawn-protected"));
            return true;
        }
        if (worldGuard.blocksClaim(player.getWorld(), position)) {
            player.sendMessage(message(player, "claim.worldguard-protected"));
            return true;
        }
        int maximum = plugin.getConfig().getInt("claims.maximum-chunks-per-land", 25);
        PaymentCharge charge = charge(player);
        if (!charge.paid()) {
            player.sendMessage(message(player, charge.messageKey(), Map.of("cost", charge.cost())));
            return true;
        }
        boolean adjacent = plugin.getConfig().getBoolean("claims.require-adjacent-chunks", true);
        LandManager.ClaimResult result;
        try {
            result = landManager.claim(player.getUniqueId(), position, maximum, adjacent);
        } catch (StorageException exception) {
            charge.refund(player);
            throw exception;
        }
        String key = switch (result.status()) {
            case CLAIMED -> "claim.claimed";
            case NO_LAND -> "claim.no-land";
            case ALREADY_YOURS -> "claim.already-yours";
            case ALREADY_CLAIMED -> "claim.already-claimed";
            case LIMIT_REACHED -> "claim.limit-reached";
            case NOT_ADJACENT -> "claim.not-adjacent";
        };
        player.sendMessage(message(player, key, Map.of(
                "name", result.land() == null ? "-" : result.land().name(),
                "count", result.claimCount(),
                "limit", maximum
        )));
        if (result.status() != LandManager.ClaimStatus.CLAIMED) charge.refund(player);
        else {
            announceCharge(player, charge);
            plugin.syncMapIntegrations();
        }
        return true;
    }

    private boolean unclaim(Player player) throws StorageException {
        return unclaim(player, position(player));
    }

    public boolean unclaimFromMenu(Player player, ChunkPosition position) throws StorageException {
        Optional<Land> land = landManager.landAt(position);
        if (land.isPresent()
                && land.get().ownerId().equals(player.getUniqueId())
                && landManager.claimCount(land.get().id()) == 1) {
            if (plugin.combatTags().blocksClaimAbandon(player)) {
                player.sendMessage(message(player, "combat.claim-abandon-blocked"));
                return true;
            }
            landManager.deleteOwnedLand(player.getUniqueId());
            player.sendMessage(message(player, "claim.unclaimed-success", Map.of("name", land.get().name(), "count", 0)));
            plugin.syncMapIntegrations();
            return true;
        }
        return unclaim(player, position);
    }

    private boolean unclaim(Player player, ChunkPosition position) throws StorageException {
        if (plugin.combatTags().blocksClaimAbandon(player)) {
            player.sendMessage(message(player, "combat.claim-abandon-blocked"));
            return true;
        }
        LandManager.UnclaimResult result = landManager.unclaim(player.getUniqueId(), position);
        String key = switch (result.status()) {
            case UNCLAIMED_SUCCESS -> "claim.unclaimed-success";
            case NO_LAND -> "claim.no-land";
            case UNCLAIMED -> "claim.unclaimed";
            case NOT_YOURS -> "claim.not-yours";
            case LAST_CHUNK -> "claim.last-chunk";
        };
        player.sendMessage(message(player, key, Map.of(
                "name", result.land() == null ? "-" : result.land().name(),
                "count", result.claimCount()
        )));
        if (result.status() == LandManager.UnclaimStatus.UNCLAIMED_SUCCESS) plugin.syncMapIntegrations();
        return true;
    }

    private boolean info(Player player) {
        player.sendMessage(message(player, "updates.checking"));
        plugin.updateChecker().refresh().thenAccept(status -> {
            if (!plugin.isEnabled()) return;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                TextComponent line = new TextComponent(TextComponent.fromLegacyText(message(player, "command.header",
                        Map.of("version", plugin.getDescription().getVersion()))));
                TextComponent badge = new TextComponent(TextComponent.fromLegacyText(updateStatus(player, status)));
                if (status.state() == UpdateChecker.State.OUTDATED && status.asset() != null && player.hasPermission("landward.admin")) {
                    badge.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/landward:claim update"));
                    badge.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(
                            plugin.localeService().message(plugin.localeService().localeFor(player), "updates.stage-info-click"))));
                }
                line.addExtra(badge);
                player.spigot().sendMessage(line);
                TextComponent author = new TextComponent(TextComponent.fromLegacyText(ChatColor.GOLD + "LandWard "
                        + ChatColor.DARK_GRAY + "» " + ChatColor.WHITE + "Maker: " + ChatColor.AQUA + "DEVKruidenier"));
                author.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/DEVKruidenier"));
                player.spigot().sendMessage(author);
            });
        });
        return true;
    }

    private String updateStatus(Player player, UpdateChecker.UpdateStatus status) {
        String locale = plugin.localeService().localeFor(player);
        return switch (status.state()) {
            case LATEST -> ChatColor.GREEN + " (Latest)";
            case OUTDATED -> outdatedStatus(player, locale, status);
            case STAGING -> ChatColor.YELLOW + " (" + plugin.localeService().message(locale, "updates.stage-info-staging") + ")";
            case STAGED -> ChatColor.GREEN + " (" + plugin.localeService().message(locale, "updates.stage-info-staged") + ")";
            case CHECKING -> ChatColor.YELLOW + " (" + plugin.localeService().message(locale, "updates.checking") + ")";
            case UNAVAILABLE, DISABLED -> "";
        };
    }

    private String outdatedStatus(Player player, String locale, UpdateChecker.UpdateStatus status) {
        return ChatColor.RED + " (Outdated: " + status.latestVersion() + ")";
    }

    private boolean list(Player player) {
        new LandMapGui(plugin, landManager).open(player);
        return true;
    }

    private boolean menu(Player player) {
        new LandManagementGui(plugin, landManager).openMain(player);
        return true;
    }

    private boolean rename(Player player, String[] arguments) throws StorageException {
        if (arguments.length < 2) {
            player.sendMessage(message(player, "land.rename-usage"));
            return true;
        }
        LandManager.RenameResult result = landManager.rename(player.getUniqueId(), String.join(" ", java.util.Arrays.copyOfRange(arguments, 1, arguments.length)));
        String key = switch (result.status()) {
            case RENAMED -> "land.renamed";
            case UNCHANGED -> "land.rename-unchanged";
            case NO_LAND -> "claim.no-land";
            case INVALID_NAME -> "land.invalid-name";
            case NAME_TAKEN -> "land.name-taken";
        };
        player.sendMessage(message(player, key, Map.of("name", result.land() == null ? "-" : result.land().name())));
        if (result.status() == LandManager.RenameStatus.RENAMED) plugin.syncMapIntegrations();
        return true;
    }

    private boolean addMember(Player player, String[] arguments) throws StorageException {
        Player target = onlineTarget(player, arguments, "land.member-usage");
        if (target == null) return true;
        LandManager.MemberResult result = landManager.addMember(player.getUniqueId(), target.getUniqueId());
        if (result.status() == LandManager.MemberStatus.ADDED) plugin.refreshAnalyticsIntegration();
        player.sendMessage(message(player, switch (result.status()) {
            case ADDED -> "land.member-added";
            case NO_LAND -> "claim.no-land";
            case CANNOT_ADD_OWNER -> "land.member-owner";
            case ALREADY_MEMBER -> "land.member-already";
            default -> "general.storage-error";
        }, Map.of("player", target.getName())));
        return true;
    }

    private boolean removeMember(Player player, String[] arguments) throws StorageException {
        Player target = onlineTarget(player, arguments, "land.member-usage");
        if (target == null) return true;
        LandManager.MemberResult result = landManager.removeMember(player.getUniqueId(), target.getUniqueId());
        if (result.status() == LandManager.MemberStatus.REMOVED) plugin.refreshAnalyticsIntegration();
        player.sendMessage(message(player, switch (result.status()) {
            case REMOVED -> "land.member-removed";
            case NO_LAND -> "claim.no-land";
            case NOT_MEMBER -> "land.member-not-found";
            default -> "general.storage-error";
        }, Map.of("player", target.getName())));
        return true;
    }

    private boolean updateRights(Player player, String[] arguments) throws StorageException {
        if (arguments.length != 4) {
            player.sendMessage(message(player, "land.rights-usage"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(arguments[1]);
        if (target == null) {
            player.sendMessage(message(player, "land.player-not-online"));
            return true;
        }
        MemberPermissions.Permission permission;
        try {
            permission = MemberPermissions.Permission.valueOf(arguments[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(message(player, "land.rights-usage"));
            return true;
        }
        boolean enabled = arguments[3].equalsIgnoreCase("on") || arguments[3].equalsIgnoreCase("true");
        if (!enabled && !arguments[3].equalsIgnoreCase("off") && !arguments[3].equalsIgnoreCase("false")) {
            player.sendMessage(message(player, "land.rights-usage"));
            return true;
        }
        LandManager.MemberResult result = landManager.updateMemberPermission(player.getUniqueId(), target.getUniqueId(), permission, enabled);
        if (result.status() == LandManager.MemberStatus.PERMISSION_UPDATED) plugin.refreshAnalyticsIntegration();
        player.sendMessage(message(player, switch (result.status()) {
            case PERMISSION_UPDATED -> "land.rights-updated";
            case NO_LAND -> "claim.no-land";
            case NOT_MEMBER -> "land.member-not-found";
            default -> "general.storage-error";
        }, Map.of("player", target.getName(), "permission", permission.name().toLowerCase(Locale.ROOT), "state", enabled ? "on" : "off")));
        return true;
    }

    private Player onlineTarget(Player player, String[] arguments, String usageKey) {
        if (arguments.length != 2) {
            player.sendMessage(message(player, usageKey));
            return null;
        }
        Player target = Bukkit.getPlayerExact(arguments[1]);
        if (target == null) {
            player.sendMessage(message(player, "land.player-not-online"));
        }
        return target;
    }

    private ChunkPosition position(Player player) {
        return new ChunkPosition(
                player.getWorld().getUID(),
                player.getLocation().getChunk().getX(),
                player.getLocation().getChunk().getZ()
        );
    }

    private boolean worldEnabled(Player player) {
        return plugin.isLandWorld(player.getWorld());
    }

    private String paymentMode() { return plugin.getConfig().getString("payments.mode", "free"); }

    private ExperiencePayments payments() {
        return new ExperiencePayments(plugin.getConfig().getInt("payments.experience-points-per-chunk", 150));
    }

    private PaymentCharge charge(Player player) {
        String mode = paymentMode().toLowerCase(Locale.ROOT);
        if (mode.equals("free")) {
            return PaymentCharge.free();
        }
        if (mode.equals("experience")) {
            ExperiencePayments payments = payments();
            if (!payments.canAfford(player)) {
                return PaymentCharge.rejected("claim.not-enough-xp", payments.cost());
            }
            payments.withdraw(player);
            return PaymentCharge.experience(payments);
        }
        if (mode.equals("vault")) {
            VaultPayments payments = new VaultPayments(plugin.getConfig().getDouble("payments.vault-money-per-chunk", 100.0D));
            if (!payments.isAvailable()) {
                return PaymentCharge.rejected("claim.vault-unavailable", payments.cost());
            }
            if (!payments.canAfford(player)) {
                return PaymentCharge.rejected("claim.not-enough-money", payments.cost());
            }
            if (!payments.withdraw(player)) {
                return PaymentCharge.rejected("claim.payment-failed", payments.cost());
            }
            return PaymentCharge.vault(payments);
        }
        plugin.getLogger().warning("Unknown payments.mode '" + mode + "'; treating claims as free.");
        return PaymentCharge.free();
    }

    /** Reports a completed charge only after the associated claim was saved. */
    private void announceCharge(Player player, PaymentCharge charge) {
        if (charge.experience != null) {
            player.sendMessage(message(player, "claim.paid-xp", Map.of("cost", charge.cost())));
        } else if (charge.vault != null) {
            player.sendMessage(message(player, "claim.paid-money", Map.of("cost", charge.cost())));
        }
    }

    private record PaymentCharge(ExperiencePayments experience, VaultPayments vault, String messageKey, double cost) {
        static PaymentCharge free() { return new PaymentCharge(null, null, null, 0.0D); }
        static PaymentCharge rejected(String messageKey, double cost) { return new PaymentCharge(null, null, messageKey, cost); }
        static PaymentCharge experience(ExperiencePayments payments) { return new PaymentCharge(payments, null, null, payments.cost()); }
        static PaymentCharge vault(VaultPayments payments) { return new PaymentCharge(null, payments, null, payments.cost()); }
        boolean paid() { return messageKey == null; }
        void refund(Player player) {
            if (experience != null) player.giveExp(experience.cost());
            if (vault != null) vault.refund(player);
        }
    }

    private String message(CommandSender sender, String key) {
        return decorate(sender, key, plugin.localeService().message(plugin.localeService().localeFor(sender), key));
    }

    private String message(CommandSender sender, String key, Map<String, ?> placeholders) {
        return decorate(sender, key, plugin.localeService().message(plugin.localeService().localeFor(sender), key, placeholders));
    }

    /** Gives every player-facing command response one recognisable LandWard identity. */
    private String decorate(CommandSender sender, String key, String text) {
        if (!(sender instanceof Player)) return text;
        String color = switch (key) {
            case "land.created", "claim.claimed", "claim.unclaimed-success", "land.member-added",
                 "land.member-removed", "land.rights-updated", "land.renamed", "land.rename-unchanged" -> "§a";
            case "general.no-permission", "general.storage-error", "general.unknown-command",
                 "claim.no-land", "claim.already-yours", "claim.already-claimed", "claim.limit-reached",
                 "claim.not-adjacent", "claim.world-disabled", "claim.worldguard-protected", "claim.spawn-protected",
                 "claim.not-yours", "claim.last-chunk", "claim.not-enough-xp", "claim.not-enough-money",
                 "claim.vault-unavailable", "claim.payment-failed", "combat.claim-abandon-blocked" -> "§c";
            default -> "§f";
        };
        return "§6§lLand§b§lWard §8> §r" + color + text;
    }
}
