package io.clueterra.questions.ai.mapper;

import io.clueterra.questions.ai.model.run.AiRun;
import io.clueterra.questions.ai.model.run.AiRunStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Component
public class AiRunMapper {

    private final Clock clock;

    public AiRunMapper(Clock clock) {
        this.clock = clock;
    }

    public AiRun toRunning(AiRun run) {
        Instant startedAt = run.startedAt() != null ? run.startedAt() : Instant.now(clock);
        return new AiRun(
                run.id(),
                run.runType(),
                run.triggerType(),
                AiRunStatus.RUNNING,
                run.provider(),
                run.model(),
                run.promptVersion(),
                run.requestedBy(),
                run.totalStages(),
                run.preparedItems(),
                run.processedItems(),
                run.changedItems(),
                run.generatedItems(),
                run.estimatedInputTokens(),
                run.estimatedOutputTokens(),
                run.actualInputTokens(),
                run.actualOutputTokens(),
                run.toolCallCount(),
                run.configuration(),
                null,
                null,
                run.createdAt(),
                startedAt,
                null
        );
    }

    public AiRun toCompleted(
            AiRun run,
            int processedItems,
            int changedItems,
            int generatedItems,
            long actualInputTokens,
            long actualOutputTokens,
            int toolCallCount,
            Map<String, Object> summary) {
        return toFinished(
                run,
                AiRunStatus.COMPLETED,
                processedItems,
                changedItems,
                generatedItems,
                actualInputTokens,
                actualOutputTokens,
                toolCallCount,
                summary,
                null
        );
    }

    public AiRun toSkipped(
            AiRun run,
            int processedItems,
            int changedItems,
            int generatedItems,
            long actualInputTokens,
            long actualOutputTokens,
            int toolCallCount,
            Map<String, Object> summary) {
        return toFinished(
                run,
                AiRunStatus.SKIPPED,
                processedItems,
                changedItems,
                generatedItems,
                actualInputTokens,
                actualOutputTokens,
                toolCallCount,
                summary,
                null
        );
    }

    public AiRun toFailed(
            AiRun run,
            int processedItems,
            int changedItems,
            int generatedItems,
            long actualInputTokens,
            long actualOutputTokens,
            int toolCallCount,
            String errorMessage) {
        return toFinished(
                run,
                AiRunStatus.FAILED,
                processedItems,
                changedItems,
                generatedItems,
                actualInputTokens,
                actualOutputTokens,
                toolCallCount,
                run.summary(),
                errorMessage
        );
    }

    private AiRun toFinished(
            AiRun run,
            AiRunStatus status,
            int processedItems,
            int changedItems,
            int generatedItems,
            long actualInputTokens,
            long actualOutputTokens,
            int toolCallCount,
            Map<String, Object> summary,
            String errorMessage) {
        return new AiRun(
                run.id(),
                run.runType(),
                run.triggerType(),
                status,
                run.provider(),
                run.model(),
                run.promptVersion(),
                run.requestedBy(),
                run.totalStages(),
                run.preparedItems(),
                processedItems,
                changedItems,
                generatedItems,
                run.estimatedInputTokens(),
                run.estimatedOutputTokens(),
                actualInputTokens,
                actualOutputTokens,
                toolCallCount,
                run.configuration(),
                summary,
                errorMessage,
                run.createdAt(),
                run.startedAt(),
                Instant.now(clock)
        );
    }
}
