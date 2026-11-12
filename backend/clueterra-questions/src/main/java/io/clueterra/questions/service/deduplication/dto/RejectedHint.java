package io.clueterra.questions.service.deduplication.dto;

import io.clueterra.questions.service.hint.lens.CandidateHint;

public record RejectedHint(
        CandidateHint hint,
        RejectionReason reason,
        String details,
        String blockingHintText,
        Double similarityScore
) {
    public static RejectedHint of(CandidateHint hint, RejectionReason reason, String details) {
        return new RejectedHint(hint, reason, details, null, null);
    }

    public static RejectedHint withSimilarity(CandidateHint hint, RejectionReason reason,
                                              String details, String blockingHintText, double similarity) {
        return new RejectedHint(hint, reason, details, blockingHintText, similarity);
    }
}
