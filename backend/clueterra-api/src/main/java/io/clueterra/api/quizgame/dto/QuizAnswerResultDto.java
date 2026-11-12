package io.clueterra.api.quizgame.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;

public record QuizAnswerResultDto(
        int selectedIndex,
        int answerIndex,
        boolean correct,
        int xpAwarded,
        @JsonRawValue String fact) {
}
