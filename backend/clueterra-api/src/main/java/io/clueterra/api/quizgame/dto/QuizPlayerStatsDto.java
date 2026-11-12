package io.clueterra.api.quizgame.dto;

public record QuizPlayerStatsDto(
        long exploredCountries,
        long masteredCountries,
        long totalCountries,
        long completedLevels,
        long totalLevels,
        long answeredQuestions,
        long correctAnswers,
        long xpAwarded) {
}
