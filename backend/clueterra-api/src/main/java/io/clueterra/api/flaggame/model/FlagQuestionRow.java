package io.clueterra.api.flaggame.model;

public record FlagQuestionRow(
        long round,
        int tier,
        String iso2,
        String hints,
        int hintsUsed,
        int attempts,
        boolean alreadyRewarded,
        int baseXp,
        int hintPenaltyPercent) {
}
