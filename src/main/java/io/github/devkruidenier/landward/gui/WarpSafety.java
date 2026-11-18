package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.model.LandWarp;
import io.github.devkruidenier.landward.platform.PlatformScheduler;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.BoundingBox;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/** Each collision query stays in its own chunk's region, including chunk-edge landings. */
final class WarpSafety {
    private WarpSafety() {}
    private record Part(boolean clear, boolean supported) {}
    static CompletableFuture<Boolean> check(PlatformScheduler scheduler, World world, LandWarp warp) {
        if (world == null || warp.y() < world.getMinHeight() + 1 || warp.y() + 1.8 >= world.getMaxHeight())
            return CompletableFuture.completedFuture(false);
        var parts = new ArrayList<CompletableFuture<Part>>();
        int minX = (int)Math.floor(warp.x() - .3), maxX = (int)Math.floor(warp.x() + .3);
        int minZ = (int)Math.floor(warp.z() - .3), maxZ = (int)Math.floor(warp.z() + .3);
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
            int left = Math.max(minX, cx * 16), right = Math.min(maxX, cx * 16 + 15);
            int near = Math.max(minZ, cz * 16), far = Math.min(maxZ, cz * 16 + 15);
            parts.add(scheduler.atChunk(world, cx, cz, () -> inspect(world, warp, left, right, near, far)));
        }
        return CompletableFuture.allOf(parts.toArray(CompletableFuture[]::new)).thenApply(ignored ->
                parts.stream().allMatch(part -> part.join().clear()) && parts.stream().anyMatch(part -> part.join().supported()))
                .exceptionally(ignored -> false);
    }
    private static Part inspect(World world, LandWarp warp, int left, int right, int near, int far) {
        if (!world.getWorldBorder().isInside(new Location(world, warp.x(), warp.y(), warp.z()))) return new Part(false, false);
        var body = new BoundingBox(warp.x() - .299999, warp.y() + 1E-7, warp.z() - .299999,
                warp.x() + .299999, warp.y() + 1.8 - 1E-7, warp.z() + .299999);
        var feet = new BoundingBox(body.getMinX(), warp.y() - .06, body.getMinZ(), body.getMaxX(), warp.y() + 1E-7, body.getMaxZ());
        boolean supported = false;
        for (int x = left; x <= right; x++) for (int z = near; z <= far; z++) {
            for (int y = Math.max(world.getMinHeight(), (int)Math.floor(warp.y()) - 2); y <= (int)Math.floor(body.getMaxY()); y++) {
                var block = world.getBlockAt(x, y, z);
                var cube = new BoundingBox(x, y, z, x + 1, y + 1, z + 1);
                if ((body.overlaps(cube) || feet.overlaps(cube)) && (block.isLiquid() || dangerous(block.getType()))) return new Part(false, false);
                for (var shape : block.getCollisionShape().getBoundingBoxes()) {
                    shape.shift(x, y, z);
                    if (shape.overlaps(body)) return new Part(false, false);
                    if (shape.overlaps(feet)) supported = true;
                }
            }
        }
        return new Part(true, supported);
    }
    private static boolean dangerous(Material type) {
        return switch (type) { case FIRE, SOUL_FIRE, LAVA, MAGMA_BLOCK, CAMPFIRE, SOUL_CAMPFIRE, CACTUS,
                SWEET_BERRY_BUSH, POWDER_SNOW, WITHER_ROSE -> true; default -> false; };
    }
}
