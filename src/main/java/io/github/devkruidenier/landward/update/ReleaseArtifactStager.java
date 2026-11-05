package io.github.devkruidenier.landward.update;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Downloads and verifies an update JAR without replacing a JAR used by Paper. */
public final class ReleaseArtifactStager {
    private final HttpClient client;
    private final Path directory;
    private final long maximumBytes;
    private final String userAgent;

    public ReleaseArtifactStager(HttpClient client, Path directory, long maximumBytes, String userAgent) {
        this.client = client; this.directory = directory; this.maximumBytes = maximumBytes; this.userAgent = userAgent;
    }

    public StageResult stage(ReleaseAsset asset) {
        if (asset.size() <= 0 || asset.size() > maximumBytes) return StageResult.failure("Release asset exceeds the configured download limit");
        String safeName = asset.name().replaceAll("[^A-Za-z0-9._-]", "_");
        if (!safeName.endsWith(".jar")) return StageResult.failure("Release asset is not a JAR file");
        Path temporary = directory.resolve(safeName + ".part");
        Path staged = directory.resolve("staged-" + safeName);
        try {
            Files.createDirectories(directory);
            Files.deleteIfExists(temporary);
            HttpRequest request = HttpRequest.newBuilder(asset.downloadUri()).header("User-Agent", userAgent).GET().build();
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(temporary));
            if (response.statusCode() != 200) return fail(temporary, "Download returned HTTP " + response.statusCode());
            long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            long actualLength = Files.size(temporary);
            if ((declaredLength > maximumBytes) || actualLength > maximumBytes || actualLength != asset.size()) {
                return fail(temporary, "Downloaded asset size did not match the verified release metadata");
            }
            verifyPluginJar(temporary, asset.sha256());
            moveAtomically(temporary, staged);
            return StageResult.success(staged);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return fail(temporary, "Download interrupted");
        } catch (Exception exception) { return fail(temporary, exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()); }
    }

    static void verifyPluginJar(Path jar, String expectedSha256) throws IOException {
        try (JarFile archive = new JarFile(jar.toFile())) {
            JarEntry descriptor = archive.getJarEntry("plugin.yml");
            if (descriptor == null) throw new IOException("Downloaded JAR has no plugin.yml");
            String pluginYml;
            try (InputStream input = archive.getInputStream(descriptor)) { pluginYml = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
            if (!pluginYml.contains("name: LandWard") || !pluginYml.contains("main: io.github.devkruidenier.landward.LandWardPlugin")) {
                throw new IOException("Downloaded JAR is not a LandWard plugin artifact");
            }
        }
        if (expectedSha256 != null && !expectedSha256.isBlank() && !expectedSha256.equalsIgnoreCase(sha256(jar))) {
            throw new IOException("Downloaded JAR checksum did not match the GitHub release metadata");
        }
    }

    private StageResult fail(Path temporary, String message) {
        try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        return StageResult.failure(message);
    }
    private void moveAtomically(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException exception) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }
    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) >= 0;) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) { throw new IOException("SHA-256 is unavailable", exception); }
    }

    public record StageResult(boolean success, Path stagedFile, String detail) {
        static StageResult success(Path stagedFile) { return new StageResult(true, stagedFile, null); }
        static StageResult failure(String detail) { return new StageResult(false, null, detail); }
    }
}
