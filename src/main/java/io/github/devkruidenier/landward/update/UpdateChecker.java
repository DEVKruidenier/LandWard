package io.github.devkruidenier.landward.update;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.console.LandWardConsole.Tone;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class UpdateChecker {
    private static final URI LATEST_RELEASE = URI.create("https://api.github.com/repos/DEVKruidenier/LandWard/releases/latest");
    public static final String RELEASES_PAGE = "https://github.com/DEVKruidenier/LandWard/releases/latest";
    private final LandWardPlugin plugin;
    private final HttpClient client;
    private final GitHubReleaseClient releases;
    private volatile UpdateStatus status = UpdateStatus.checking();
    private CompletableFuture<UpdateStatus> lookup;
    private CompletableFuture<ReleaseArtifactStager.StageResult> download;

    public enum State { CHECKING, LATEST, OUTDATED, STAGING, STAGED, UNAVAILABLE, DISABLED }
    public record UpdateStatus(State state, String latestVersion, ReleaseAsset asset, Path stagedFile) {
        static UpdateStatus checking() { return new UpdateStatus(State.CHECKING, null, null, null); }
    }

    public UpdateChecker(LandWardPlugin plugin) {
        this.plugin = plugin;
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        releases = new GitHubReleaseClient(client, LATEST_RELEASE,
                "LandWard/" + plugin.getDescription().getVersion() + " (https://github.com/DEVKruidenier/LandWard)");
    }

    public void check() {
        refresh().thenAccept(current -> onMain(() -> {
            if (current.state() == State.OUTDATED) {
                new UpdateNotifications(plugin).console(current);
            } else if (current.state() == State.LATEST) {
                plugin.console().info(plugin.localeService().console("updates.up-to-date"),
                        Map.of("version", plugin.getDescription().getVersion()), Tone.SUCCESS);
            } else if (current.state() == State.UNAVAILABLE) {
                plugin.console().warning(plugin.localeService().console("updates.failed"),
                        Map.of("reason", plugin.localeService().console("updates.stage-unavailable")));
            }
        }));
    }

    /** Every explicit info/download request revalidates GitHub; concurrent checks are shared. */
    public synchronized CompletableFuture<UpdateStatus> refresh() {
        if (!plugin.getConfig().getBoolean("updates.enabled", true)) {
            status = new UpdateStatus(State.DISABLED, null, null, null);
            return CompletableFuture.completedFuture(status);
        }
        if (status.state() == State.STAGING) return CompletableFuture.completedFuture(status);
        if (lookup != null && !lookup.isDone()) return lookup;
        UpdateStatus previous = status;
        status = UpdateStatus.checking();
        lookup = releases.latest().handle((release, error) -> {
            UpdateStatus result;
            if (!plugin.getConfig().getBoolean("updates.enabled", true)) result = new UpdateStatus(State.DISABLED, null, null, null);
            else if (error != null) result = new UpdateStatus(State.UNAVAILABLE, null, null, null);
            else if (VersionComparator.isNewer(release.version(), plugin.getDescription().getVersion())) {
                result = previous.state() == State.STAGED && release.version().equals(previous.latestVersion())
                        && java.util.Objects.equals(release.asset(), previous.asset())
                        ? previous : new UpdateStatus(State.OUTDATED, release.version(), release.asset(), null);
            } else result = new UpdateStatus(State.LATEST, release.version(), null, null);
            status = result;
            return result;
        });
        return lookup;
    }

    public UpdateStatus status() { return status; }

    /** Always refresh before choosing a download; the running plugin is never overwritten. */
    public synchronized CompletableFuture<ReleaseArtifactStager.StageResult> stageLatestRelease(Consumer<String> started) {
        if (download != null && !download.isDone()) return download;
        long maximum = Math.max(1L, plugin.getConfig().getLong("updates.maximum-download-megabytes", 32L)) * 1024L * 1024L;
        var stager = new ReleaseArtifactStager(client, plugin.getDataFolder().toPath().resolve("updates"), maximum,
                "LandWard/" + plugin.getDescription().getVersion());
        download = refresh().thenCompose(current -> {
            if ((current.state() != State.OUTDATED && current.state() != State.STAGED) || current.asset() == null)
                return CompletableFuture.completedFuture(ReleaseArtifactStager.StageResult.failure("No verified newer release is available"));
            status = new UpdateStatus(State.STAGING, current.latestVersion(), current.asset(), null);
            onMain(() -> started.accept(current.latestVersion()));
            return CompletableFuture.supplyAsync(() -> stager.stage(current.asset())).thenApply(result -> {
                status = result.success()
                        ? new UpdateStatus(State.STAGED, current.latestVersion(), current.asset(), result.stagedFile())
                        : new UpdateStatus(State.OUTDATED, current.latestVersion(), current.asset(), null);
                return result;
            });
        });
        return download;
    }

    private void onMain(Runnable action) {
        if (plugin.isEnabled()) plugin.getServer().getScheduler().runTask(plugin, action);
    }
}
