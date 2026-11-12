package io.clueterra.api.quizgame.model;

import com.fasterxml.jackson.databind.JsonNode;

public record QuizQuestionRow(
        int position,
        long id,
        String kind,
        JsonNode subject,
        JsonNode options,
        int answerIndex,
        JsonNode fact,
        QuizGivenAnswerRow answer) {
}
