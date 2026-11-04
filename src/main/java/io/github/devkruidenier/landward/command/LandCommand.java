package io.github.devkruidenier.landward.command;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.gui.LandMapGui;
import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.model.Land;
import io.github.devkruidenier.landward.model.MemberPermissions;
import io.github.devkruidenier.landward.storage.StorageException;
import io.github.devkruidenier.landward.economy.ExperiencePayments;
import io.github.devkruidenier.landward.economy.VaultPayments;
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

    private static final List<String> ROOT_COMPLETIONS = List.of("help", "create", "claim", "unclaim", "info", "list", "menu", "add", "remove", "rights");

    private final LandWardPlugin plugin;
    private final LandManager landManager;

    public LandCommand(LandWardPlugin plugin, LandManager landManager) {
        this.plugin = plugin;
        this.landManager = landManager;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] arguments
    ) {
        if (arguments.length == 0 || (arguments.length == 1 && arguments[0].equalsIgnoreCase("help"))) {
            sendHelp(sender);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(message(sender, "general.player-only"));
            return true;
        }

        try {
            return switch (arguments[0].toLowerCase(Locale.ROOT)) {
                case "create" -> create(player, arguments);
                case "claim" -> claim(player);
                case "unclaim" -> unclaim(player);
                case "info" -> info(player);
                case "list" -> list(player);
                case "menu" -> list(player);
                case "add" -> addMember(player, arguments);
                case "remove" -> removeMember(player, arguments);
                case "rights" -> updateRights(player, arguments);
                default -> {
                    sender.sendMessage(message(sender, "general.unknown-command"));
                    yield true;
                }
            };
        } catch (StorageException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Land command storage operation failed", exception);
            sender.sendMessage(message(sender, "general.storage-error"));
            return true;
        }
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
                .filter(completion -> completion.startsWith(prefix))
                .toList();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(message(sender, "command.header", Map.of(
                "version", plugin.getPluginMeta().getVersion()
        )));
        sender.sendMessage(message(sender, "command.description"));
        sender.sendMessage(message(sender, "command.help"));
        sender.sendMessage(message(sender, "command.help-create"));
        sender.sendMessage(message(sender, "command.help-claim"));
        sender.sendMessage(message(sender, "command.help-unclaim"));
        sender.sendMessage(message(sender, "command.help-info"));
        sender.sendMessage(message(sender, "command.help-list"));
        sender.sendMessage(message(sender, "command.help-menu"));
        sender.sendMessage(message(sender, "command.help-members"));
    }

    private boolean create(Player player, String[] arguments) throws StorageException {
        if (!worldEnabled(player)) {
            player.sendMessage(message(player, "claim.world-disabled"));
            return true;
        }
        if (arguments.length < 2) {
            player.sendMessage(message(player, "land.create-usage"));
            return true;
        }

        String name = String.join(" ", java.util.Arrays.copyOfRange(arguments, 1, arguments.length));
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
        return true;
    }

    private boolean claim(Player player) throws StorageException {
        if (!worldEnabled(player)) {
            player.sendMessage(message(player, "claim.world-disabled"));
            return true;
        }

        ChunkPosition position = position(player);
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
        return true;
    }

    private boolean unclaim(Player player) throws StorageException {
        LandManager.UnclaimResult result = landManager.unclaim(player.getUniqueId(), position(player));
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
        return true;
    }

    private boolean info(Player player) {
        Optional<Land> land = landManager.landAt(position(player));
        if (land.isEmpty()) {
            player.sendMessage(message(player, "claim.unclaimed"));
            return true;
        }
        Land claimedLand = land.get();
        player.sendMessage(message(player, "claim.info", Map.of(
                "name", claimedLand.name(),
                "count", landManager.claimCount(claimedLand.id())
        )));
        return true;
    }

    private boolean list(Player player) {
        new LandMapGui(plugin, landManager).open(player);
        return true;
    }

    private boolean addMember(Player player, String[] arguments) throws StorageException {
        Player target = onlineTarget(player, arguments, "land.member-usage");
        if (target == null) return true;
        LandManager.MemberResult result = landManager.addMember(player.getUniqueId(), target.getUniqueId());
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
        String world = player.getWorld().getName();
        List<String> enabled = plugin.getConfig().getStringList("claims.enabled-worlds");
        List<String> disabled = plugin.getConfig().getStringList("claims.disabled-worlds");
        return !disabled.contains(world) && (enabled.isEmpty() || enabled.contains(world));
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
        return plugin.localeService().message(plugin.localeService().localeFor(sender), key);
    }

    private String message(CommandSender sender, String key, Map<String, ?> placeholders) {
        return plugin.localeService().message(plugin.localeService().localeFor(sender), key, placeholders);
    }
}
