package io.clueterra.api.admin.quizgame.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record QuizQuestionPreviewDto(
        long id,
        String kind,
        int level,
        boolean active,
        JsonNode subject,
        JsonNode options,
        int answerIndex,
        JsonNode fact,
        long answers,
        long correct,
        String generatedAt) {
}
