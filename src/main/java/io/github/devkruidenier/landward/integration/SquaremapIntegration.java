package io.github.devkruidenier.landward.integration;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.ClaimedChunk;
import io.github.devkruidenier.landward.model.Land;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import java.awt.Color;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/** Reflective Squaremap bridge: it never loads Squaremap API classes when Squaremap is absent. */
public final class SquaremapIntegration {
    private static final Color ADMIN_PURPLE = new Color(168, 85, 247);
    private final LandWardPlugin plugin; private final LandManager lands; private boolean failureLogged;
    public SquaremapIntegration(LandWardPlugin plugin, LandManager lands) { this.plugin = plugin; this.lands = lands; }
    public void sync() {
        Plugin squaremap = plugin.getServer().getPluginManager().getPlugin("squaremap");
        if (!plugin.getConfig().getBoolean("integrations.squaremap.enabled", true) || squaremap == null || !squaremap.isEnabled()) return;
        try {
            Object map = Class.forName("xyz.jpenilla.squaremap.api.SquaremapProvider").getMethod("get").invoke(null);
            Object layerKey = key("landward_claims");
            for (World world : Bukkit.getWorlds()) syncWorld(map, layerKey, world);
        } catch (ReflectiveOperationException exception) { if (!failureLogged) { plugin.getLogger().warning("Squaremap markers could not be synchronized: " + exception.getMessage()); failureLogged = true; } }
    }
    private void syncWorld(Object map, Object layerKey, World world) throws ReflectiveOperationException {
        if (!plugin.isLandWorld(world)) return;
        Class<?> id = Class.forName("xyz.jpenilla.squaremap.api.WorldIdentifier");
        // Squaremap indexes worlds by their dimension key (minecraft:overworld),
        // not by Bukkit's folder/display name (usually just "world").
        NamespacedKey worldKey = world.getKey();
        Object worldId = id.getMethod("create", String.class, String.class)
                .invoke(null, worldKey.getNamespace(), worldKey.getKey());
        @SuppressWarnings("unchecked") Optional<Object> found = (Optional<Object>) call(map, "getWorldIfEnabled", worldId);
        if (found.isEmpty()) return;
        Object registry = call(found.get(), "layerRegistry");
        if ((boolean) call(registry, "hasEntry", layerKey)) call(registry, "unregister", layerKey);
        Object layer = layer();
        for (ClaimedChunk claim : lands.claims()) {
            if (!claim.position().worldId().equals(world.getUID())) continue;
            Land land = lands.land(claim.landId()).orElse(null); if (land == null) continue;
            call(layer, "addMarker", key("claim_" + claim.position().x() + '_' + claim.position().z()), marker(land, claim));
        }
        call(registry, "register", layerKey, layer);
    }
    private Object key(String value) throws ReflectiveOperationException { Class<?> type = Class.forName("xyz.jpenilla.squaremap.api.Key"); return type.getMethod("of", String.class).invoke(null, value); }
    private Object layer() throws ReflectiveOperationException { Object builder = Class.forName("xyz.jpenilla.squaremap.api.SimpleLayerProvider").getMethod("builder", String.class).invoke(null, "LandWard Claims"); call(builder, "showControls", true); call(builder, "defaultHidden", false); return call(builder, "build"); }
    private Object marker(Land land, ClaimedChunk claim) throws ReflectiveOperationException {
        Class<?> point = Class.forName("xyz.jpenilla.squaremap.api.Point"); Method of = point.getMethod("of", double.class, double.class); int x = claim.position().minBlockX(), z = claim.position().minBlockZ();
        Object points = List.of(of.invoke(null, (double) x, (double) z), of.invoke(null, (double) claim.position().maxBlockXExclusive(), (double) z), of.invoke(null, (double) claim.position().maxBlockXExclusive(), (double) claim.position().maxBlockZExclusive()), of.invoke(null, (double) x, (double) claim.position().maxBlockZExclusive()));
        Object polygon = Class.forName("xyz.jpenilla.squaremap.api.marker.Marker").getMethod("polygon", List.class).invoke(null, points);
        Object options = Class.forName("xyz.jpenilla.squaremap.api.marker.MarkerOptions").getMethod("builder").invoke(null);
        Color color = lands.isAdministrative(land) ? ADMIN_PURPLE : Color.GREEN;
        String tooltip = (lands.isAdministrative(land) ? "LandWard admin claim: " : "LandWard: ") + land.name();
        call(options, "strokeColor", color); call(options, "fillColor", color); call(options, "fillOpacity", 0.20D); call(options, "hoverTooltip", tooltip); call(polygon, "markerOptions", options); return polygon;
    }
    private static Object call(Object target, String name, Object... args) throws ReflectiveOperationException {
        for (Method method : target.getClass().getMethods()) { if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue; Class<?>[] types = method.getParameterTypes(); boolean match = true; for (int i = 0; i < types.length; i++) if (args[i] != null && !wrap(types[i]).isInstance(args[i])) { match = false; break; } if (match) return method.invoke(target, args); }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }
    private static Class<?> wrap(Class<?> type) { if (!type.isPrimitive()) return type; if (type == boolean.class) return Boolean.class; if (type == double.class) return Double.class; return type; }
}
