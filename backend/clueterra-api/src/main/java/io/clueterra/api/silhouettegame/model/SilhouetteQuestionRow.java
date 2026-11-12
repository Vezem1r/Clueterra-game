package io.clueterra.api.silhouettegame.model;

public record SilhouetteQuestionRow(
        long round,
        int tier,
        String iso2,
        String shapePath,
        String hints,
        int hintsUsed,
        int attempts,
        boolean alreadyRewarded,
        int baseXp,
        int hintPenaltyPercent,
        SilhouettePhotoRow photo) {
}
