package io.clueterra.questions.ai.model.run;

public record AiRunPreparationResult(
        Long runId,
        AiRunType runType,
        AiRunStatus status,
        int preparedStages,
        int preparedItems,
        long estimatedInputTokens,
        String message) {

    public static AiRunPreparationResult skipped(AiRunType runType, String message) {
        return new AiRunPreparationResult(null, runType, AiRunStatus.SKIPPED, 0, 0, 0, message);
    }
}
