package io.github.devkruidenier.landward.gui;

import java.util.Set;

final class WarpLimits {
    private WarpLimits() {}
    static int resolve(Set<String> positiveNodes, int fallback) {
        if (positiveNodes.contains("landward.warps.unlimited")) return -1;
        int highest = -1;
        for (String node : positiveNodes) {
            if (!node.matches("landward\\.warps\\.[0-9]+")) continue;
            try { highest = Math.max(highest, Integer.parseInt(node.substring("landward.warps.".length()))); }
            catch (NumberFormatException ignored) { }
        }
        return highest >= 0 ? highest : Math.max(0, fallback);
    }
}
