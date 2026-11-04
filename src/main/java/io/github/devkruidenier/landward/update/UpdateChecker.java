package io.github.devkruidenier.landward.update;

import io.github.devkruidenier.landward.LandWardPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateChecker {

    private static final URI LATEST_RELEASE = URI.create(
            "https://api.github.com/repos/DEVKruidenier/LandWard/releases/latest"
    );
    private static final String RELEASES_PAGE = "https://github.com/DEVKruidenier/LandWard/releases";
    private static final Pattern TAG_PATTERN = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final LandWardPlugin plugin;
    private final HttpClient client;

    public UpdateChecker(LandWardPlugin plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void check() {
        if (!plugin.getConfig().getBoolean("updates.enabled", true)) {
            return;
        }

        String currentVersion = plugin.getPluginMeta().getVersion();
        HttpRequest request = HttpRequest.newBuilder(LATEST_RELEASE)
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "LandWard/" + currentVersion + " (https://github.com/DEVKruidenier/LandWard)")
                .GET()
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, error) -> {
                    if (error != null) {
                        log("updates.failed", Map.of("reason", readableError(error)));
                        return;
                    }
                    handleResponse(response, currentVersion);
                });
    }

    private void handleResponse(HttpResponse<String> response, String currentVersion) {
        if (response.statusCode() == 404) {
            log("updates.no-release", Map.of());
            return;
        }
        if (response.statusCode() != 200) {
            log("updates.failed-status", Map.of("status", response.statusCode()));
            return;
        }

        Matcher matcher = TAG_PATTERN.matcher(response.body());
        if (!matcher.find()) {
            log("updates.failed", Map.of("reason", "invalid GitHub response"));
            return;
        }

        String latestVersion = matcher.group(1);
        if (VersionComparator.isNewer(latestVersion, currentVersion)) {
            log("updates.available", Map.of(
                    "latest", latestVersion,
                    "current", currentVersion,
                    "url", RELEASES_PAGE
            ));
            return;
        }

        log("updates.up-to-date", Map.of("version", currentVersion));
    }

    private void log(String key, Map<String, ?> placeholders) {
        if (!plugin.isEnabled()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getLogger().info(plugin.localeService().console(key, placeholders))
        );
    }

    private String readableError(Throwable error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
