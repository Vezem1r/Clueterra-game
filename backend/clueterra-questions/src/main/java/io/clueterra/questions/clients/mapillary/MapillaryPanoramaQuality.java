package io.clueterra.questions.clients.mapillary;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

final class MapillaryPanoramaQuality {

    private static final int MIN_WIDTH = 5_000;
    private static final double MIN_QUALITY_SCORE = 0.75;
    private static final double MAX_ASPECT_RATIO_DEVIATION = 0.05;

    private MapillaryPanoramaQuality() {
    }

    static Optional<String> usableOriginalUrl(JsonNode image) {
        String originalUrl = image.path("thumb_original_url").asText(null);
        int width = image.path("width").asInt(0);
        int height = image.path("height").asInt(0);
        JsonNode qualityScore = image.path("quality_score");

        if (!image.path("is_pano").asBoolean(false)
                || originalUrl == null || originalUrl.isBlank()
                || width < MIN_WIDTH || height <= 0
                || Math.abs((double) width / height - 2.0) > MAX_ASPECT_RATIO_DEVIATION
                || !qualityScore.isNumber()
                || qualityScore.asDouble() < MIN_QUALITY_SCORE) {
            return Optional.empty();
        }
        return Optional.of(originalUrl);
    }
}
