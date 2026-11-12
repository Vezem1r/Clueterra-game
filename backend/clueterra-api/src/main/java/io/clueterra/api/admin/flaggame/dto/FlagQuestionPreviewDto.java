package io.clueterra.api.admin.flaggame.dto;

import java.util.List;

public record FlagQuestionPreviewDto(
        long id,
        String countryCode,
        String name,
        int tier,
        String region,
        String subregion,
        boolean active,
        String flagUrl,
        List<FlagHintPreviewDto> hints,
        long players,
        long solved,
        long skips,
        long reveals,
        long wrongGuesses,
        Double averageHints,
        String generatedAt) {
}
