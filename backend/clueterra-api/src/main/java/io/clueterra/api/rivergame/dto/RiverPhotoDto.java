package io.clueterra.api.rivergame.dto;

public record RiverPhotoDto(
        String url,
        String authorName,
        String licenseLabel,
        String licenseUrl,
        String sourcePageUrl) {
}
