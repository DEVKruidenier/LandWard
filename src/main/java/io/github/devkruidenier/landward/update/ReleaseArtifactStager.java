package io.github.devkruidenier.landward.update;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
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
        if (asset.size() <= 0 || asset.size() > maximumBytes) return StageResult.failure("updates.failure-metadata");
        String safeName = asset.name();
        if (safeName == null || !safeName.matches("LandWard-[A-Za-z0-9][A-Za-z0-9._-]*\\.jar")
                || safeName.contains("..")) return StageResult.failure("updates.failure-metadata");
        Path temporary = null;
        Path staged = directory.resolve(safeName);
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, ".landward-", ".part");
            HttpRequest request = HttpRequest.newBuilder(asset.downloadUri()).header("User-Agent", userAgent).GET().build();
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(temporary));
            if (response.statusCode() != 200) return fail(temporary, "updates.failure-download");
            long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            long actualLength = Files.size(temporary);
            if ((declaredLength > maximumBytes) || actualLength > maximumBytes || actualLength != asset.size()) {
                return fail(temporary, "updates.failure-verification");
            }
            try { verifyPluginJar(temporary, asset.sha256()); }
            catch (IOException exception) { return fail(temporary, "updates.failure-verification"); }
            publishVerified(temporary, staged, asset.sha256());
            return StageResult.success(staged);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return fail(temporary, "updates.failure-download");
        } catch (java.nio.file.FileAlreadyExistsException exception) { return fail(temporary, "updates.failure-existing"); }
        catch (Exception exception) { return fail(temporary, "updates.failure-download"); }
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
        try { if (temporary != null) Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        return StageResult.failure(message);
    }
    private static synchronized void publishVerified(Path source, Path target, String digest) throws IOException {
        if (Files.exists(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(target) || !Files.isRegularFile(target)) throw new java.nio.file.FileAlreadyExistsException(target.toString());
            try { verifyPluginJar(target, digest); }
            catch (IOException exception) { throw new java.nio.file.FileAlreadyExistsException(target.toString()); }
            if (!sha256(source).equals(sha256(target))) throw new java.nio.file.FileAlreadyExistsException(target.toString());
            Files.delete(source);
            return;
        }
        // An atomic no-clobber publication: a competing target can never be replaced.
        // Both names are on the same filesystem; the source has already been closed and verified.
        try { Files.createLink(target, source); Files.delete(source); }
        catch (UnsupportedOperationException exception) { Files.move(source, target); }
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
