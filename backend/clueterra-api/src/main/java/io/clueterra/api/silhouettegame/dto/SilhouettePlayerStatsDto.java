package io.clueterra.api.silhouettegame.dto;

public record SilhouettePlayerStatsDto(
        long attemptedCountries,
        long solvedCountries,
        long totalCountries,
        long answerAttempts,
        long skips,
        long xpAwarded) {
}
