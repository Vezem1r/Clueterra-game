package io.clueterra.questions.ai.model.review;

public record AiHintReviewItemResult(
        Long hintId,
        String decision,
        String factStatus,
        String anchorStatus,
        String textStatus,
        String difficultyStatus,
        String reasonCode,
        Double confidence,
        String summaryReason,
        String evidenceSummary,
        String proposedText,
        Integer proposedDifficulty,
        String proposedImageUrl,
        String disableReason,
        Integer retryAfterDays) {

    public AiHintReviewItemResult(Long hintId, String decision, String factStatus, String anchorStatus,
                                  String textStatus, String difficultyStatus, String reasonCode,
                                  Double confidence, String summaryReason, String evidenceSummary,
                                  String proposedText, Integer proposedDifficulty,
                                  String proposedImageUrl, String disableReason) {
        this(hintId, decision, factStatus, anchorStatus, textStatus, difficultyStatus, reasonCode,
                confidence, summaryReason, evidenceSummary, proposedText, proposedDifficulty,
                proposedImageUrl, disableReason, null);
    }
}
