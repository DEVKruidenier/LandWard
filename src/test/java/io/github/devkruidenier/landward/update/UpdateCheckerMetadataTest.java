package io.github.devkruidenier.landward.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UpdateCheckerMetadataTest {
    private static final String DIGEST = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void acceptsOnlyOfficialDigestVerifiedLandWardJarAssets() {
        String response = "{\"assets\":[{\"name\":\"LandWard-1.2.0.jar\",\"size\":123,\"digest\":\"sha256:" + DIGEST
                + "\",\"browser_download_url\":\"https://github.com/DEVKruidenier/LandWard/releases/download/v1.2.0/LandWard-1.2.0.jar\"}]}";
        ReleaseAsset asset = ReleaseMetadataParser.releaseAsset(response, "1.2.0");
        assertEquals("LandWard-1.2.0.jar", asset.name());
        assertEquals(DIGEST, asset.sha256());
    }

    @Test
    void rejectsAssetsWithoutDigestOrOutsideOfficialRepository() {
        String unsigned = "{\"name\":\"LandWard.jar\",\"size\":123,\"browser_download_url\":\"https://github.com/DEVKruidenier/LandWard/releases/download/v1/LandWard.jar\"}";
        String foreign = "{\"name\":\"LandWard.jar\",\"size\":123,\"digest\":\"sha256:" + DIGEST
                + "\",\"browser_download_url\":\"https://github.com/example/other/releases/download/v1/LandWard.jar\"}";
        assertNull(ReleaseMetadataParser.releaseAsset(unsigned, "1"));
        assertNull(ReleaseMetadataParser.releaseAsset(foreign, "1"));
    }
}
