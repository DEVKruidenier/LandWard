package io.github.devkruidenier.landward.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReleaseArtifactStagerTest {
    @TempDir Path directory;

    @Test
    void followsDownloadRedirectAndVerifiesTheDownloadedJar() throws Exception {
        Path valid = jar("redirect-source.jar", "name: LandWard\nmain: io.github.devkruidenier.landward.LandWardPlugin\n");
        byte[] bytes = java.nio.file.Files.readAllBytes(valid);
        String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/file");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.createContext("/file", exchange -> {
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            var client = java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.NORMAL).build();
            var stager = new ReleaseArtifactStager(client, directory.resolve("updates"), 1024 * 1024, "LandWard test");
            var asset = new ReleaseAsset("1.0.1.6", "LandWard-1.0.1.6.jar",
                    java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/redirect"), bytes.length, digest);
            var result = stager.stage(asset);
            org.junit.jupiter.api.Assertions.assertTrue(result.success(), result.detail());
            org.junit.jupiter.api.Assertions.assertArrayEquals(bytes, java.nio.file.Files.readAllBytes(result.stagedFile()));
        } finally { server.stop(0); }
    }

    @Test
    void acceptsOnlyLandWardPluginArtifacts() throws Exception {
        Path valid = jar("valid.jar", "name: LandWard\nmain: io.github.devkruidenier.landward.LandWardPlugin\n");
        Path invalid = jar("invalid.jar", "name: DifferentPlugin\nmain: example.Main\n");
        assertDoesNotThrow(() -> ReleaseArtifactStager.verifyPluginJar(valid, null));
        assertThrows(IOException.class, () -> ReleaseArtifactStager.verifyPluginJar(invalid, null));
    }

    private Path jar(String name, String pluginYml) throws IOException {
        Path file = directory.resolve(name);
        try (JarOutputStream output = new JarOutputStream(java.nio.file.Files.newOutputStream(file))) {
            output.putNextEntry(new JarEntry("plugin.yml"));
            output.write(pluginYml.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return file;
    }
}
