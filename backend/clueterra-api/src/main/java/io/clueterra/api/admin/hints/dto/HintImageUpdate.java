package io.clueterra.api.admin.hints.dto;

public record HintImageUpdate(
        String imageUrl,
        String originalImageUrl,
        String imageSourcePageUrl,
        String imageAuthorName,
        String imageAttributionText,
        String imageLicenseLabel,
        String imageLicenseUrl,
        String contentRevisionHash) {
}
