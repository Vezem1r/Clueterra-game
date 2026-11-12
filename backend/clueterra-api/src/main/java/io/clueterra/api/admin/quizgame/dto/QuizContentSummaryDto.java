package io.clueterra.api.admin.quizgame.dto;

public record QuizContentSummaryDto(
        long activeCountries,
        long activeQuestions,
        long players,
        long explored,
        long answers,
        long correct) {
}
