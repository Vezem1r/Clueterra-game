package io.clueterra.api.admin.silhouettegame.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonRawValue;

public record SilhouetteQuestionPreviewDto(
        long id,
        String countryCode,
        String name,
        int tier,
        String region,
        String subregion,
        String shapePath,
        boolean active,
        @JsonRawValue String facts,
        List<SilhouetteHintPreviewDto> hints,
        String photoUrl,
        String photoAuthorName,
        String photoSourcePageUrl,
        String photoLicenseLabel,
        String photoLicenseUrl,
        Integer photoWidth,
        Integer photoHeight,
        String photoSelection,
        String photoSelectedBy,
        String landmarkQid,
        String landmarkName,
        Integer landmarkSitelinks,
        long players,
        long solved,
        long skips,
        long reveals,
        Double averageHints,
        String generatedAt) {
}
