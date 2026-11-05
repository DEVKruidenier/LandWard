package io.github.devkruidenier.landward.update;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.console.LandWardConsole.Tone;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
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
    private volatile UpdateStatus status = UpdateStatus.checking();

    public enum State { CHECKING, LATEST, OUTDATED, STAGING, STAGED, UNAVAILABLE, DISABLED }

    public record UpdateStatus(State state, String latestVersion, ReleaseAsset asset, Path stagedFile) {
        static UpdateStatus checking() { return new UpdateStatus(State.CHECKING, null, null, null); }
    }

    public UpdateChecker(LandWardPlugin plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void check() {
        if (!plugin.getConfig().getBoolean("updates.enabled", true)) {
            status = new UpdateStatus(State.DISABLED, null, null, null);
            return;
        }
        status = UpdateStatus.checking();

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
                        status = new UpdateStatus(State.UNAVAILABLE, null, null, null);
                        log("updates.failed", Map.of("reason", readableError(error)));
                        return;
                    }
                    handleResponse(response, currentVersion);
                });
    }

    private void handleResponse(HttpResponse<String> response, String currentVersion) {
        if (response.statusCode() == 404) {
            status = new UpdateStatus(State.UNAVAILABLE, null, null, null);
            log("updates.no-release", Map.of());
            return;
        }
        if (response.statusCode() != 200) {
            status = new UpdateStatus(State.UNAVAILABLE, null, null, null);
            log("updates.failed-status", Map.of("status", response.statusCode()));
            return;
        }

        Matcher matcher = TAG_PATTERN.matcher(response.body());
        if (!matcher.find()) {
            status = new UpdateStatus(State.UNAVAILABLE, null, null, null);
            log("updates.failed", Map.of("reason", "invalid GitHub response"));
            return;
        }

        String latestVersion = matcher.group(1);
        if (VersionComparator.isNewer(latestVersion, currentVersion)) {
            ReleaseAsset asset = ReleaseMetadataParser.releaseAsset(response.body(), latestVersion);
            status = new UpdateStatus(State.OUTDATED, latestVersion, asset, null);
            log("updates.available", Map.of(
                    "latest", latestVersion,
                    "current", currentVersion,
                    "url", RELEASES_PAGE
            ));
            return;
        }

        status = new UpdateStatus(State.LATEST, latestVersion, null, null);
        log("updates.up-to-date", Map.of("version", currentVersion));
    }

    public UpdateStatus status() {
        return status;
    }

    /** Downloads the verified release asset to a staging directory; it never replaces a running plugin JAR. */
    public CompletableFuture<ReleaseArtifactStager.StageResult> stageLatestRelease() {
        UpdateStatus current = status;
        if (current.state() != State.OUTDATED || current.asset() == null) {
            return CompletableFuture.completedFuture(ReleaseArtifactStager.StageResult.failure("No verified update asset is available"));
        }
        status = new UpdateStatus(State.STAGING, current.latestVersion(), current.asset(), null);
        long maximumBytes = Math.max(1L, plugin.getConfig().getLong("updates.maximum-download-megabytes", 32L)) * 1024L * 1024L;
        ReleaseArtifactStager stager = new ReleaseArtifactStager(client, plugin.getDataFolder().toPath().resolve("updates"), maximumBytes,
                "LandWard/" + plugin.getPluginMeta().getVersion() + " (https://github.com/DEVKruidenier/LandWard)");
        return CompletableFuture.supplyAsync(() -> stager.stage(current.asset())).thenApply(result -> {
            status = result.success()
                    ? new UpdateStatus(State.STAGED, current.latestVersion(), current.asset(), result.stagedFile())
                    : new UpdateStatus(State.OUTDATED, current.latestVersion(), current.asset(), null);
            return result;
        });
    }

    private void log(String key, Map<String, ?> placeholders) {
        if (!plugin.isEnabled()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () ->
                logOnMainThread(key, placeholders)
        );
    }

    private void logOnMainThread(String key, Map<String, ?> placeholders) {
        if (key.equals("updates.failed") || key.equals("updates.failed-status")) {
            plugin.console().warning(plugin.localeService().console(key), placeholders);
            return;
        }

        Tone tone = switch (key) {
            case "updates.up-to-date" -> Tone.SUCCESS;
            case "updates.available" -> Tone.BRAND;
            default -> Tone.WARNING;
        };
        plugin.console().info(plugin.localeService().console(key), placeholders, tone);
    }

    private String readableError(Throwable error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
