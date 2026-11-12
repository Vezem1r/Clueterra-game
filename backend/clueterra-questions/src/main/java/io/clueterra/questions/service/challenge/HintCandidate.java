package io.clueterra.questions.service.challenge;

import io.clueterra.questions.entity.Hint;
import io.clueterra.questions.entity.enums.HintBand;

public record HintCandidate(
        Hint hint,
        HintBand band,
        boolean hasImage,
        String sourceFamily,
        String textFingerprint) {

    public static HintCandidate from(Hint hint) {
        HintBand band =
                hint.getDifficulty() != null
                        ? HintBand.fromDifficulty(hint.getDifficulty())
                        : HintBand.MEDIUM_HINT;

        boolean hasImage = hint.getImageUrl() != null && !hint.getImageUrl().isBlank();

        String sourceFamily = computeSourceFamily(hint.getSourceIndicator());

        String textFingerprint = computeTextFingerprint(hint.getHintText());

        return new HintCandidate(hint, band, hasImage, sourceFamily, textFingerprint);
    }

    private static String computeSourceFamily(String sourceIndicator) {
        if (sourceIndicator == null) return "";
        return sourceIndicator.replaceAll("_\\d+$", "");
    }

    private static String computeTextFingerprint(String text) {
        if (text == null || text.isBlank()) return "";
        String normalized = text.toLowerCase().trim()
                .replaceAll("\\d+", "#")
                .replaceAll("\\s+", " ");
        return Integer.toHexString(normalized.hashCode());
    }
}
