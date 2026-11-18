package io.github.devkruidenier.landward.metrics;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Bounded, immutable categories only; never accepts land names or server addresses. */
public record UsageSnapshot(Map<String, String> charts, Map<String, Boolean> integrations) {
    public static final Set<String> INTEGRATIONS = Set.of("WorldGuard", "Vault", "PlaceholderAPI", "BlueMap", "squaremap", "Pl3xMap", "Plan");

    public UsageSnapshot {
        charts = Map.copyOf(charts);
        integrations = Map.copyOf(integrations);
    }

    public static String bucket(long count) {
        if (count < 0) throw new IllegalArgumentException("Negative usage count");
        if (count == 0) return "0";
        if (count <= 10) return "1-10";
        if (count <= 50) return "11-50";
        if (count <= 100) return "51-100";
        if (count <= 500) return "101-500";
        return "501+";
    }

    public static String language(String configured, Set<String> supported) {
        if (configured == null) return "en_US";
        String[] parts = configured.replace('-', '_').split("_", 2);
        String normalized = parts.length == 2
                ? parts[0].toLowerCase(Locale.ROOT) + "_" + parts[1].toUpperCase(Locale.ROOT) : configured;
        return supported.contains(normalized) ? normalized : "en_US";
    }

    public static String paymentMode(String configured) {
        String value = configured == null ? "free" : configured.toLowerCase(Locale.ROOT);
        return Set.of("free", "experience", "vault").contains(value) ? value : "free";
    }

    public Map<String, Map<String, Integer>> integrationChart() {
        var result = new java.util.HashMap<String, Map<String, Integer>>();
        for (String name : INTEGRATIONS) {
            result.put(name, Map.of(Boolean.TRUE.equals(integrations.get(name)) ? "Enabled" : "Disabled", 1));
        }
        return result;
    }
}
