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
        server.createContext("/invalid", exchange -> {
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(new byte[bytes.length]); }
        });
        server.createContext("/failed", exchange -> { exchange.sendResponseHeaders(503, -1); exchange.close(); });
        server.createContext("/interrupted", exchange -> {
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(new byte[5]); }
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
            org.junit.jupiter.api.Assertions.assertEquals(asset.name(), result.stagedFile().getFileName().toString());
            org.junit.jupiter.api.Assertions.assertTrue(stager.stage(asset).success(), "Identical verified download is reusable");
            java.nio.file.Files.delete(result.stagedFile());
            try (var tasks = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var concurrent = new java.util.ArrayList<java.util.concurrent.Future<ReleaseArtifactStager.StageResult>>();
                for (int i = 0; i < 4; i++) concurrent.add(tasks.submit(() -> stager.stage(asset)));
                for (var task : concurrent) org.junit.jupiter.api.Assertions.assertTrue(task.get().success());
            }
            java.nio.file.Files.writeString(result.stagedFile(), "existing file must survive");
            org.junit.jupiter.api.Assertions.assertFalse(stager.stage(asset).success());
            org.junit.jupiter.api.Assertions.assertEquals("existing file must survive", java.nio.file.Files.readString(result.stagedFile()));
            java.nio.file.Files.delete(result.stagedFile());
            for (String name : new String[]{"../LandWard-test.jar", "C:LandWard-test.jar", "LandWard-../bad.jar", "staged-LandWard-test.jar"}) {
                org.junit.jupiter.api.Assertions.assertFalse(stager.stage(new ReleaseAsset(asset.version(), name, asset.downloadUri(), bytes.length, digest)).success());
            }
            org.junit.jupiter.api.Assertions.assertFalse(stager.stage(new ReleaseAsset(asset.version(), asset.name(), asset.downloadUri(), bytes.length, "0".repeat(64))).success());
            org.junit.jupiter.api.Assertions.assertFalse(java.nio.file.Files.exists(result.stagedFile()));
            for (String path : new String[]{"/invalid", "/failed", "/interrupted"}) {
                var failure = new ReleaseAsset(asset.version(), asset.name(), asset.downloadUri().resolve(path), bytes.length, digest);
                org.junit.jupiter.api.Assertions.assertFalse(stager.stage(failure).success(), path);
                org.junit.jupiter.api.Assertions.assertFalse(java.nio.file.Files.exists(result.stagedFile()));
            }
            try (var files = java.nio.file.Files.list(directory.resolve("updates"))) {
                org.junit.jupiter.api.Assertions.assertEquals(0, files.count(), "No partial files remain after failures");
            }
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
