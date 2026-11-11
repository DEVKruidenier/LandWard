package io.github.devkruidenier.landward.update;

import com.google.gson.JsonParser;
import java.net.URI;

/** Parses asset boundaries and selects only the universal official release JAR. */
final class ReleaseMetadataParser {
    private ReleaseMetadataParser() {}

    static ReleaseAsset releaseAsset(String body, String version) {
        try {
            var root = JsonParser.parseString(body).getAsJsonObject();
            if (!root.has("assets") || !root.get("assets").isJsonArray()) return null;
            String numeric = version.replaceFirst("^[vV]", "");
            String expected = "LandWard-" + numeric + ".jar";
            for (var entry : root.getAsJsonArray("assets")) {
                try {
                    var asset = entry.getAsJsonObject();
                    if (!expected.equals(asset.get("name").getAsString())) continue;
                    URI uri = URI.create(asset.get("browser_download_url").getAsString());
                    String path = "/DEVKruidenier/LandWard/releases/download/";
                    if (!"https".equalsIgnoreCase(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getHost())
                            || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getQuery() != null || uri.getFragment() != null
                            || !(uri.getPath().equals(path + numeric + "/" + expected)
                            || uri.getPath().equals(path + "v" + numeric + "/" + expected))) continue;
                    String digest = asset.get("digest").getAsString();
                    long size = asset.get("size").getAsLong();
                    if (!digest.matches("sha256:[a-fA-F0-9]{64}") || size <= 0) continue;
                    return new ReleaseAsset(version, expected, uri, size, digest.substring(7));
                } catch (RuntimeException invalidAsset) { /* Keep checking other assets. */ }
            }
        } catch (RuntimeException invalidMetadata) { /* Never download invalid metadata. */ }
        return null;
    }
}
