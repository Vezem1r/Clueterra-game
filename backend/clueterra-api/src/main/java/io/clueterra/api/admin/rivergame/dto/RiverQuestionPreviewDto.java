package io.clueterra.api.admin.rivergame.dto;

import java.util.List;

public record RiverQuestionPreviewDto(
        long id,
        String countryCode,
        String countryName,
        String name,
        int rank,
        double lengthKm,
        String plainImageUrl,
        String bordersImageUrl,
        boolean active,
        List<RiverHintPreviewDto> hints,
        String photoUrl,
        String photoAuthorName,
        String photoLicenseLabel,
        String wikipediaUrl) {
}
