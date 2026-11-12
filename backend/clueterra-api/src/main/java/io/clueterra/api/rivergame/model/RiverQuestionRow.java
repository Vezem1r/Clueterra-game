package io.clueterra.api.rivergame.model;

public record RiverQuestionRow(
        long id,
        short rankInCountry,
        String plainImageKey,
        String bordersImageKey,
        String course,
        short hintsUsed,
        int attempts,
        long roundNumber,
        boolean alreadyRewarded,
        String revealedHints,
        int baseXp,
        int hintPenaltyPercent) {
}
