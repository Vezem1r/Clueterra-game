package io.clueterra.api.admin.silhouettegame.model;

import java.util.Map;

public record SilhouettePhotoUpdate(
        String objectKey,
        String originalUrl,
        String sourcePageUrl,
        String authorName,
        String attributionText,
        String licenseLabel,
        String licenseUrl,
        String sourceSha1,
        String mimeType,
        int width,
        int height,
        long byteSize,
        String contentSha256,
        String landmarkQid,
        Map<String, String> landmarkNames,
        Integer landmarkSitelinks) {
}
