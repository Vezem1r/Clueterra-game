package io.clueterra.questions.service.challenge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.clueterra.questions.entity.Hint;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChallengeHintEntry(
        int countryOrder,
        int hintOrder,
        Long hintId,
        String text,
        Integer difficulty,
        String category,
        String type,
        String source,
        Long imageResourceId,
        String imageUrl,
        String originalImageUrl,
        String imageSourcePageUrl,
        String imageAuthorName,
        String imageAttributionText,
        String imageLicenseLabel,
        String imageLicenseUrl
) {
    private static final String UNKNOWN_SOURCE = "UNKNOWN";

    public static ChallengeHintEntry of(int countryOrder, int hintOrder, Hint hint) {
        return new ChallengeHintEntry(
                countryOrder,
                hintOrder,
                hint.getId(),
                hint.getHintText(),
                hint.getDifficulty(),
                hint.getCategory().name(),
                hint.getHintType().name(),
                hint.getSource() != null ? hint.getSource().name() : UNKNOWN_SOURCE,
                hint.getImageResourceId(),
                hint.getImageUrl(),
                hint.getOriginalImageUrl(),
                hint.getImageSourcePageUrl(),
                hint.getImageAuthorName(),
                hint.getImageAttributionText(),
                hint.getImageLicenseLabel(),
                hint.getImageLicenseUrl());
    }
}
