package io.clueterra.api.quizgame.dto;

public record QuizAnswerDto(QuizAnswerResultDto answer, QuizStopDto stop, QuizPlayerStatsDto stats, boolean complete) {
}
