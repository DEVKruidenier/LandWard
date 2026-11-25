package io.github.devkruidenier.landward.blockprotection;

import java.util.*;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.*;

/** Runs on the owning region. Derives the partner coordinate without reading its region. */
public final class ProtectionBlocks {
  private ProtectionBlocks() {}

  public static BlockKey key(Block b) {
    return new BlockKey(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
  }

  public static boolean supported(Material m) {
    return m == Material.CHEST
        || m == Material.TRAPPED_CHEST
        || m == Material.BARREL
        || Tag.SHULKER_BOXES.isTagged(m)
        || Tag.DOORS.isTagged(m)
        || Tag.TRAPDOORS.isTagged(m)
        || Tag.FENCE_GATES.isTagged(m);
  }

  public record Part(BlockKey key, String material, List<BlockKey> unit, String shape) {}

  public static Part part(Block b) {
    if (!supported(b.getType())) throw new IllegalArgumentException("unsupported");
    var key = key(b);
    var unit = new ArrayList<BlockKey>();
    unit.add(key);
    var data = b.getBlockData();
    String shape = b.getType().name();
    if (data instanceof Door door) {
      unit.add(
          new BlockKey(
              key.world(),
              key.x(),
              key.y() + (door.getHalf() == Bisected.Half.TOP ? -1 : 1),
              key.z()));
      shape += ":" + door.getFacing() + ":" + door.getHinge();
    } else if (data instanceof Chest chest && chest.getType() != Chest.Type.SINGLE) {
      var face = chest.getFacing();
      BlockFace side =
          switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> throw new IllegalArgumentException("unsupported");
          };
      if (chest.getType() == Chest.Type.RIGHT) side = side.getOppositeFace();
      unit.add(
          new BlockKey(key.world(), key.x() + side.getModX(), key.y(), key.z() + side.getModZ()));
      shape += ":" + face;
    }
    return new Part(key, b.getType().name(), List.copyOf(unit), shape);
  }
}
