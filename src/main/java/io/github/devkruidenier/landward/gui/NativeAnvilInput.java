package io.github.devkruidenier.landward.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.inventory.view.builder.InventoryViewBuilder;
import net.kyori.adventure.text.Component;
import java.lang.reflect.InvocationTargetException;

/** Creates a native menu that receives rename packets on both Bukkit implementations. */
final class NativeAnvilInput {
    private NativeAnvilInput() {}

    static AnvilView create(Player player, String title) {
        // Bukkit.createInventory(ANVIL) opens CraftContainer on Spigot, which
        // has anvil-shaped slots but is not the native rename-packet handler.
        var builder = MenuType.ANVIL.builder().checkReachable(false);
        // Spigot requires the title before build(). Paper replaces the String
        // builder method with a Component method. Invoke the public API
        // interface to avoid depending on CraftBukkit implementation classes.
        try {
            try {
                InventoryViewBuilder.class.getMethod("title", String.class).invoke(builder, title);
            } catch (NoSuchMethodException spigotSignatureAbsent) {
                InventoryViewBuilder.class.getMethod("title", Component.class)
                        .invoke(builder, Component.text(title));
            }
        } catch (ReflectiveOperationException exception) {
            Throwable cause = exception instanceof InvocationTargetException invocation
                    ? invocation.getCause() : exception;
            throw new IllegalStateException("Could not configure native anvil title", cause);
        }
        return builder.build(player);
    }
}
