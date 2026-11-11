package io.github.devkruidenier.landward.update;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.jar.JarFile;

/** Explicit development-only check against GitHub; not run by tests or included in plugin JARs. */
public final class UpdaterFixture {
    public static void main(String[] arguments) throws Exception {
        var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        var release = new GitHubReleaseClient(client,
                URI.create("https://api.github.com/repos/DEVKruidenier/LandWard/releases/latest"), "LandWard updater verification").latest().join();
        if (!VersionComparator.isNewer(release.version(), "1.0.0.1") || release.asset() == null)
            throw new IllegalStateException("No current verified upgrade from 1.0.0.1");
        var result = new ReleaseArtifactStager(client, Path.of(arguments[0]), 32L * 1024 * 1024,
                "LandWard updater verification").stage(release.asset());
        if (!result.success()) throw new IllegalStateException(result.detail());
        try (var jar = new JarFile(result.stagedFile().toFile()); var input = jar.getInputStream(jar.getJarEntry("plugin.yml"))) {
            String descriptor = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (!descriptor.contains("version: '" + release.version().replaceFirst("^[vV]", "") + "'"))
                throw new IllegalStateException("Downloaded plugin version does not match latest release");
        }
        System.out.println("Verified upgrade from 1.0.0.1 to GitHub latest " + release.version());
        System.out.println("Universal asset, redirect, SHA256, byte count and packaged version verified: " + result.stagedFile());
    }
}
