package io.clueterra.api.admin.quizgame.dto;

import java.util.List;

public record QuizStopPreviewDto(
        long id,
        String countryCode,
        String name,
        int tier,
        String region,
        String subregion,
        boolean active,
        List<String> neighbours,
        List<QuizQuestionPreviewDto> questions,
        long visitors,
        long explored) {
}
