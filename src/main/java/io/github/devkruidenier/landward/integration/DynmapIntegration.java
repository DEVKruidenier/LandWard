package io.github.devkruidenier.landward.integration;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.ClaimedChunk;
import io.github.devkruidenier.landward.model.Land;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.lang.reflect.Method;
import java.util.List;

/** Optional Dynmap marker layer, accessed reflectively to keep Dynmap optional. */
public final class DynmapIntegration {
    private static final String SET_ID = "landward.claims";
    private final LandWardPlugin plugin;
    private final LandManager landManager;
    private boolean failureLogged;

    public DynmapIntegration(LandWardPlugin plugin, LandManager landManager) {
        this.plugin = plugin;
        this.landManager = landManager;
    }

    public void sync() {
        if (!plugin.getConfig().getBoolean("integrations.dynmap.enabled", true)
                || plugin.getServer().getPluginManager().getPlugin("dynmap") == null) return;
        try {
            Object dynmap = plugin.getServer().getPluginManager().getPlugin("dynmap");
            Object markerApi = invoke(dynmap, "getMarkerAPI");
            if (markerApi == null) return;
            Object priorSet = invoke(markerApi, "getMarkerSet", SET_ID);
            if (priorSet != null) invoke(priorSet, "deleteMarkerSet");
            Object markerSet = invoke(markerApi, "createMarkerSet", SET_ID, "LandWard Claims", null, true);
            if (markerSet == null) return;
            for (ClaimedChunk claim : landManager.claims()) createChunkMarker(markerSet, claim);
        } catch (ReflectiveOperationException exception) {
            if (!failureLogged) {
                plugin.getLogger().warning("Dynmap was detected but markers could not be synchronized: " + exception.getMessage());
                failureLogged = true;
            }
        }
    }

    private void createChunkMarker(Object markerSet, ClaimedChunk claim) throws ReflectiveOperationException {
        World world = Bukkit.getWorld(claim.position().worldId());
        Land land = landManager.land(claim.landId()).orElse(null);
        if (world == null || land == null || !plugin.isLandWorld(world)) return;
        int minX = claim.position().minBlockX(), minZ = claim.position().minBlockZ();
        double[] x = {minX, claim.position().maxBlockXExclusive(), claim.position().maxBlockXExclusive(), minX};
        double[] z = {minZ, minZ, claim.position().maxBlockZExclusive(), claim.position().maxBlockZExclusive()};
        String id = "landward_" + land.id() + "_" + claim.position().worldId() + "_" + claim.position().x() + "_" + claim.position().z();
        Object marker = invoke(markerSet, "createAreaMarker", id, land.name(), false, world.getName(), x, z, true);
        if (marker != null) {
            invoke(marker, "setFillStyle", 0.20D, 0x35C759);
            invoke(marker, "setLineStyle", 2, 0.80D, 0x35C759);
        }
    }

    private static Object invoke(Object target, String name, Object... arguments) throws ReflectiveOperationException {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            Class<?>[] parameters = method.getParameterTypes();
            boolean match = true;
            for (int index = 0; index < parameters.length; index++) {
                if (arguments[index] != null && !wrap(parameters[index]).isInstance(arguments[index])) { match = false; break; }
            }
            if (match) return method.invoke(target, arguments);
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == double.class) return Double.class;
        if (type == boolean.class) return Boolean.class;
        return type;
    }
}
