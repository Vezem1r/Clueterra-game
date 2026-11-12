package io.clueterra.api.rivergame.dto;

public record RiverPlayerStatsDto(
        long attemptedRivers,
        long solvedRivers,
        long answerAttempts,
        long skips,
        long xpAwarded,
        Long currentQuestionId) {
}
