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
