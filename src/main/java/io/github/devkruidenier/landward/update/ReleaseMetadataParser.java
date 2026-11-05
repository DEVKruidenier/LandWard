package io.github.devkruidenier.landward.update;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small dependency-free parser for the limited GitHub release fields LandWard trusts. */
final class ReleaseMetadataParser {
    private static final Pattern JAR_ASSET_PATTERN = Pattern.compile(
            "\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+\\.jar)\\\"[\\s\\S]{0,16384}?\\\"size\\\"\\s*:\\s*(\\d+)[\\s\\S]{0,16384}?\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\""
    );
    private static final Pattern DIGEST_PATTERN = Pattern.compile("\\\"digest\\\"\\s*:\\s*\\\"sha256:([a-fA-F0-9]{64})\\\"");

    private ReleaseMetadataParser() { }

    static ReleaseAsset releaseAsset(String body, String version) {
        Matcher asset = JAR_ASSET_PATTERN.matcher(body);
        while (asset.find()) {
            try {
                URI uri = URI.create(asset.group(3));
                if (!"https".equalsIgnoreCase(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getHost())
                        || !uri.getPath().startsWith("/DEVKruidenier/LandWard/releases/download/")) continue;
                Matcher digest = DIGEST_PATTERN.matcher(asset.group());
                if (!digest.find()) continue;
                return new ReleaseAsset(version, asset.group(1), uri, Long.parseLong(asset.group(2)), digest.group(1));
            } catch (IllegalArgumentException ignored) { }
        }
        return null;
    }
}
