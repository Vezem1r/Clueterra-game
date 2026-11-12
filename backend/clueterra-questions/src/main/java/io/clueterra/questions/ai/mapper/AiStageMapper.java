package io.clueterra.questions.ai.mapper;

import io.clueterra.questions.ai.model.run.AiRunStage;
import io.clueterra.questions.ai.model.run.AiStageExecutionResult;
import io.clueterra.questions.ai.model.run.AiStageStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Component
public class AiStageMapper {

    private final Clock clock;

    public AiStageMapper(Clock clock) {
        this.clock = clock;
    }

    public AiRunStage toRunning(AiRunStage stage) {
        return new AiRunStage(
                stage.id(),
                stage.runId(),
                stage.stageIndex(),
                stage.stageType(),
                AiStageStatus.RUNNING,
                stage.itemCount(),
                stage.estimatedInputTokens(),
                stage.estimatedOutputTokens(),
                stage.actualInputTokens(),
                stage.actualOutputTokens(),
                stage.imageCount(),
                stage.payload(),
                stage.resultSummary(),
                null,
                stage.providerRequestId(),
                stage.createdAt(),
                Instant.now(clock),
                null
        );
    }

    public AiRunStage toSkipped(AiRunStage stage, String errorMessage) {
        return toStageWithStatus(
                stage,
                AiStageStatus.SKIPPED,
                stage.actualInputTokens(),
                stage.actualOutputTokens(),
                stage.resultSummary(),
                errorMessage,
                stage.providerRequestId(),
                stage.startedAt(),
                Instant.now(clock)
        );
    }

    public AiRunStage toFailed(AiRunStage stage, String errorMessage) {
        return toStageWithStatus(
                stage,
                AiStageStatus.FAILED,
                stage.actualInputTokens(),
                stage.actualOutputTokens(),
                stage.resultSummary(),
                errorMessage,
                stage.providerRequestId(),
                stage.startedAt(),
                Instant.now(clock)
        );
    }

    public AiRunStage toCompleted(AiRunStage stage, AiStageExecutionResult result) {
        return toStageWithStatus(
                stage,
                AiStageStatus.COMPLETED,
                result.actualInputTokens(),
                result.actualOutputTokens(),
                result.resultSummary(),
                null,
                result.providerRequestId(),
                stage.startedAt(),
                Instant.now(clock)
        );
    }

    private AiRunStage toStageWithStatus(
            AiRunStage stage,
            AiStageStatus status,
            long actualInputTokens,
            long actualOutputTokens,
            Map<String, Object> resultSummary,
            String errorMessage,
            String providerRequestId,
            Instant startedAt,
            Instant completedAt) {
        return new AiRunStage(
                stage.id(),
                stage.runId(),
                stage.stageIndex(),
                stage.stageType(),
                status,
                stage.itemCount(),
                stage.estimatedInputTokens(),
                stage.estimatedOutputTokens(),
                actualInputTokens,
                actualOutputTokens,
                stage.imageCount(),
                stage.payload(),
                resultSummary,
                errorMessage,
                providerRequestId,
                stage.createdAt(),
                startedAt,
                completedAt
        );
    }
}
