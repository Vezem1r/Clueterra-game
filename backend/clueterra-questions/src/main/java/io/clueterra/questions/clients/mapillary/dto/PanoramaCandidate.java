package io.clueterra.questions.clients.mapillary.dto;

public record PanoramaCandidate(
        long id,
        String imageUrl,
        long capturedAt,
        TileCoord tile,
        double lat,
        double lon,
        String creatorUsername,
        String sequenceId) {
}
