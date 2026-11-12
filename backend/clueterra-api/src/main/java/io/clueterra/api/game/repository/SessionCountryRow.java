package io.clueterra.api.game.repository;

public record SessionCountryRow(
        long id,
        long countryId,
        int countryOrder,
        int totalHints,
        int hintsRevealed,
        int attemptsUsed,
        boolean isGuessed,
        long timeSpentMs,
        int xpEarned,
        String isoAlpha2,
        String nameEn,
        String flagEmoji,
        String flagUrl,
        String region) {
}
