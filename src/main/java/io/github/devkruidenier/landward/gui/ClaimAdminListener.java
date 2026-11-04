package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Handles the administrative claim menu without exposing mutating actions to players. */
public final class ClaimAdminListener implements Listener {
    private final LandWardPlugin plugin;
    private final LandManager lands;

    public ClaimAdminListener(LandWardPlugin plugin, LandManager lands) { this.plugin = plugin; this.lands = lands; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ClaimAdminHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (!player.hasPermission("landward.admin")) { player.closeInventory(); return; }
        ClaimAdminGui gui = new ClaimAdminGui(plugin, lands);
        if (holder.screen() == ClaimAdminHolder.Screen.MAIN) {
            if (event.getRawSlot() == 11) new LandMapGui(plugin, lands).openAdmin(player);
            else if (event.getRawSlot() == 13) {
                plugin.reloadLandWard();
                player.sendMessage(Component.text("LandWard » ", NamedTextColor.GOLD)
                        .append(Component.text(plugin.localeService().message(plugin.localeService().localeFor(player), "general.reloaded"), NamedTextColor.GREEN)));
                gui.openMain(player);
            }
            else if (event.getRawSlot() == 15) gui.openWorlds(player);
            else if (event.getRawSlot() == 26) player.closeInventory();
            return;
        }
        if (event.getRawSlot() == 53) { gui.openMain(player); return; }
        String worldName = holder.worldsBySlot().get(event.getRawSlot());
        if (worldName == null) return;
        World world = Bukkit.getWorld(worldName);
        if (world == null) { gui.openWorlds(player); return; }
        plugin.setLandWorldEnabled(world, !plugin.isLandWorld(world));
        gui.openWorlds(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ClaimAdminHolder) event.setCancelled(true);
    }
}
