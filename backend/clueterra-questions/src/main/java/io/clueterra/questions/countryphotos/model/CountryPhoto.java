package io.clueterra.questions.countryphotos.model;

import java.util.Map;

public record CountryPhoto(
        String objectKey,
        String originalUrl,
        String sourcePageUrl,
        String authorName,
        String attributionText,
        String licenseLabel,
        String licenseUrl,
        String sourceSha1,
        String mimeType,
        Integer width,
        Integer height,
        Long byteSize,
        String contentSha256,
        String landmarkQid,
        Map<String, String> landmarkNames,
        Integer landmarkSitelinks) {
}
