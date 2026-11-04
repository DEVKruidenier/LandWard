package io.github.devkruidenier.landward.integration;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.ClaimedChunk;
import io.github.devkruidenier.landward.model.Land;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Optional BlueMap shape-marker bridge with no bundled BlueMap dependency. */
public final class BlueMapIntegration {
    private static final String SET_ID = "landward.claims";
    private final LandWardPlugin plugin;
    private final LandManager landManager;
    private boolean failureLogged;

    public BlueMapIntegration(LandWardPlugin plugin, LandManager landManager) { this.plugin = plugin; this.landManager = landManager; }

    public void sync() {
        if (!plugin.getConfig().getBoolean("integrations.bluemap.enabled", true)
                || plugin.getServer().getPluginManager().getPlugin("BlueMap") == null) return;
        try {
            Class<?> apiClass = Class.forName("de.bluecolored.bluemap.api.BlueMapAPI");
            @SuppressWarnings("unchecked") Optional<Object> optionalApi = (Optional<Object>) apiClass.getMethod("getInstance").invoke(null);
            if (optionalApi.isEmpty()) return;
            Object api = optionalApi.get();
            clearExistingMarkerSets(api);
            for (ClaimedChunk claim : landManager.claims()) syncClaim(api, claim);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            if (!failureLogged) { plugin.getLogger().warning("BlueMap was detected but markers could not be synchronized: " + exception.getMessage()); failureLogged = true; }
        }
    }

    private void clearExistingMarkerSets(Object api) throws ReflectiveOperationException {
        @SuppressWarnings("unchecked") Collection<Object> maps = (Collection<Object>) invoke(api, "getMaps");
        for (Object map : maps) {
            @SuppressWarnings("unchecked") Map<String, Object> sets = (Map<String, Object>) invoke(map, "getMarkerSets");
            sets.remove(SET_ID);
        }
    }

    private void syncClaim(Object api, ClaimedChunk claim) throws ReflectiveOperationException {
        World world = Bukkit.getWorld(claim.position().worldId());
        Land land = landManager.land(claim.landId()).orElse(null);
        if (world == null || land == null) return;
        @SuppressWarnings("unchecked") Optional<Object> blueWorld = (Optional<Object>) invoke(api, "getWorld", world);
        if (blueWorld.isEmpty()) return;
        @SuppressWarnings("unchecked") Collection<Object> maps = (Collection<Object>) invoke(blueWorld.get(), "getMaps");
        Object set = markerSet(land, claim);
        for (Object map : maps) {
            @SuppressWarnings("unchecked") Map<String, Object> sets = (Map<String, Object>) invoke(map, "getMarkerSets");
            Object targetSet = sets.get(SET_ID);
            if (targetSet == null) { targetSet = set; sets.put(SET_ID, targetSet); }
            @SuppressWarnings("unchecked") Map<String, Object> markers = (Map<String, Object>) invoke(targetSet, "getMarkers");
            markers.put(markerId(claim), marker(land, claim));
        }
    }

    private Object markerSet(Land land, ClaimedChunk claim) throws ReflectiveOperationException {
        Class<?> type = Class.forName("de.bluecolored.bluemap.api.markers.MarkerSet");
        Constructor<?> constructor = type.getConstructor(String.class, boolean.class, boolean.class);
        return constructor.newInstance("LandWard Claims", true, false);
    }

    private Object marker(Land land, ClaimedChunk claim) throws ReflectiveOperationException {
        int minX = claim.position().x() << 4, minZ = claim.position().z() << 4;
        Class<?> shapeType = Class.forName("de.bluecolored.bluemap.api.math.Shape");
        Object shape = shapeType.getMethod("createRect", double.class, double.class, double.class, double.class)
                .invoke(null, (double) minX, (double) minZ, minX + 16.0, minZ + 16.0);
        Class<?> markerType = Class.forName("de.bluecolored.bluemap.api.markers.ShapeMarker");
        Object marker = markerType.getConstructor(String.class, shapeType, float.class).newInstance(land.name(), shape, 320.0F);
        invoke(marker, "setDepthTestEnabled", false);
        invoke(marker, "setDetail", "LandWard claim: " + land.name());
        return marker;
    }

    private static String markerId(ClaimedChunk claim) { return "chunk_" + claim.position().worldId() + "_" + claim.position().x() + "_" + claim.position().z(); }
    private static Object invoke(Object target, String name, Object... arguments) throws ReflectiveOperationException {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            Class<?>[] parameters = method.getParameterTypes(); boolean matches = true;
            for (int i = 0; i < parameters.length; i++) if (arguments[i] != null && !wrap(parameters[i]).isInstance(arguments[i])) { matches = false; break; }
            if (matches) return method.invoke(target, arguments);
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }
    private static Class<?> wrap(Class<?> type) { if (!type.isPrimitive()) return type; if (type == boolean.class) return Boolean.class; if (type == float.class) return Float.class; return type; }
}
