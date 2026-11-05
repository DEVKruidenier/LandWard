package io.github.devkruidenier.landward.update;

import java.net.URI;

/** A downloadable LandWard release asset advertised by GitHub Releases. */
public record ReleaseAsset(String version, String name, URI downloadUri, long size, String sha256) {
}
