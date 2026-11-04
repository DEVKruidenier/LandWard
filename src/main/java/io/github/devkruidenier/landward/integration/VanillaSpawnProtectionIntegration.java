package io.github.devkruidenier.landward.integration;

import io.github.devkruidenier.landward.model.ChunkPosition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** Prevents LandWard claims from covering the server's built-in spawn-protection square. */
public final class VanillaSpawnProtectionIntegration {

    public boolean blocksClaim(ChunkPosition position) {
        int radius = Bukkit.getServer().getSpawnRadius();
        if (radius <= 0 || Bukkit.getWorlds().isEmpty()) return false;

        World primaryWorld = Bukkit.getWorlds().getFirst();
        if (!primaryWorld.getUID().equals(position.worldId())) return false;
        Location spawn = primaryWorld.getSpawnLocation();
        int minimumX = spawn.getBlockX() - radius;
        int maximumX = spawn.getBlockX() + radius;
        int minimumZ = spawn.getBlockZ() - radius;
        int maximumZ = spawn.getBlockZ() + radius;
        int chunkMinimumX = position.x() << 4;
        int chunkMinimumZ = position.z() << 4;
        return chunkMinimumX <= maximumX && chunkMinimumX + 15 >= minimumX
                && chunkMinimumZ <= maximumZ && chunkMinimumZ + 15 >= minimumZ;
    }
}
