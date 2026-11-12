package io.clueterra.api.flaggame.dto;

public record FlagPlayerStatsDto(
        long attemptedFlags,
        long solvedFlags,
        long totalFlags,
        long answerAttempts,
        long skips,
        long xpAwarded) {
}
