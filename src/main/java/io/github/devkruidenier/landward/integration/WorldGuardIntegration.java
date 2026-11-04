package io.github.devkruidenier.landward.integration;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.model.ChunkPosition;
import org.bukkit.World;

import java.lang.reflect.Method;
import java.util.Map;

/** Optional WorldGuard bridge without a transitive dependency conflict on Paper. */
public final class WorldGuardIntegration {
    private final LandWardPlugin plugin;
    private boolean failureLogged;

    public WorldGuardIntegration(LandWardPlugin plugin) { this.plugin = plugin; }

    public boolean blocksClaim(World world, ChunkPosition chunk) {
        if (!plugin.getConfig().getBoolean("integrations.worldguard.enabled", true)
                || !plugin.getConfig().getBoolean("integrations.worldguard.deny-any-overlap", true)
                || plugin.getServer().getPluginManager().getPlugin("WorldGuard") == null) return false;
        try {
            Object worldGuard = Class.forName("com.sk89q.worldguard.WorldGuard").getMethod("getInstance").invoke(null);
            Object platform = invoke(worldGuard, "getPlatform");
            Object container = invoke(platform, "getRegionContainer");
            Object adaptedWorld = invokeStatic("com.sk89q.worldedit.bukkit.BukkitAdapter", "adapt", world);
            Object manager = invoke(container, "get", adaptedWorld);
            if (manager == null) return false;
            @SuppressWarnings("unchecked") Map<Object, Object> regions = (Map<Object, Object>) invoke(manager, "getRegions");
            return overlapsAnyRegion(regions, chunk);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            if (!failureLogged) {
                plugin.getLogger().warning("WorldGuard was detected but could not be queried. Claims are denied to protect its regions: " + exception.getMessage());
                failureLogged = true;
            }
            return true;
        }
    }

    private boolean overlapsAnyRegion(Map<Object, Object> regions, ChunkPosition chunk) throws ReflectiveOperationException {
        int minChunkX = chunk.x() << 4, maxChunkX = minChunkX + 15;
        int minChunkZ = chunk.z() << 4, maxChunkZ = minChunkZ + 15;
        for (Object region : regions.values()) {
            Object min = invoke(region, "getMinimumPoint"), max = invoke(region, "getMaximumPoint");
            int startX = Math.max(minChunkX, coordinate(min, "x")), endX = Math.min(maxChunkX, coordinate(max, "x"));
            int startZ = Math.max(minChunkZ, coordinate(min, "z")), endZ = Math.min(maxChunkZ, coordinate(max, "z"));
            if (startX > endX || startZ > endZ) continue;
            int y = coordinate(min, "y");
            for (int x = startX; x <= endX; x++) for (int z = startZ; z <= endZ; z++) {
                Object point = invokeStatic("com.sk89q.worldedit.math.BlockVector3", "at", x, y, z);
                if ((boolean) invoke(region, "contains", point)) return true;
            }
        }
        return false;
    }

    private static int coordinate(Object vector, String method) throws ReflectiveOperationException { return (int) invoke(vector, method); }
    private static Object invokeStatic(String className, String method, Object... arguments) throws ReflectiveOperationException { return invokeMethod(Class.forName(className), null, method, arguments); }
    private static Object invoke(Object target, String method, Object... arguments) throws ReflectiveOperationException { return invokeMethod(target.getClass(), target, method, arguments); }

    private static Object invokeMethod(Class<?> type, Object target, String name, Object... arguments) throws ReflectiveOperationException {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            Class<?>[] parameters = method.getParameterTypes();
            boolean matches = true;
            for (int i = 0; i < parameters.length; i++) if (arguments[i] != null && !wrap(parameters[i]).isInstance(arguments[i])) { matches = false; break; }
            if (matches) return method.invoke(target, arguments);
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == boolean.class) return Boolean.class;
        return type;
    }
}
