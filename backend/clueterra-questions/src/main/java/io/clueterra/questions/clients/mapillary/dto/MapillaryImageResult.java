package io.clueterra.questions.clients.mapillary.dto;

public record MapillaryImageResult(
        long id,
        double latitude,
        double longitude,
        String imageUrl,
        double qualityScore,
        long capturedAt,
        String creatorUsername,
        String sequenceId
) {
}
