package io.github.devkruidenier.landward.update;

import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Fetches current official metadata instead of retaining a startup snapshot. */
final class GitHubReleaseClient {
    private final HttpClient client;
    private final URI endpoint;
    private final String userAgent;

    GitHubReleaseClient(HttpClient client, URI endpoint, String userAgent) {
        this.client = client; this.endpoint = endpoint; this.userAgent = userAgent;
    }

    CompletableFuture<Release> latest() {
        var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(8))
                .header("Accept", "application/vnd.github+json").header("User-Agent", userAgent)
                .header("Cache-Control", "no-cache").GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() != 200) throw new IllegalStateException("GitHub returned HTTP " + response.statusCode());
            var root = JsonParser.parseString(response.body()).getAsJsonObject();
            if ((root.has("draft") && root.get("draft").getAsBoolean())
                    || (root.has("prerelease") && root.get("prerelease").getAsBoolean()))
                throw new IllegalStateException("GitHub returned an unpublished or prerelease build");
            String version = root.get("tag_name").getAsString();
            if (version.isBlank()) throw new IllegalStateException("Missing GitHub release version");
            return new Release(version, ReleaseMetadataParser.releaseAsset(response.body(), version));
        });
    }

    record Release(String version, ReleaseAsset asset) {}
}
