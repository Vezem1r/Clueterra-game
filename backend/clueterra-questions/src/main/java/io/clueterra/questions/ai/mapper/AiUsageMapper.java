package io.clueterra.questions.ai.mapper;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import io.clueterra.questions.ai.model.run.AiRun;
import io.clueterra.questions.ai.model.run.AiRunStage;
import io.clueterra.questions.ai.model.run.AiRunUsage;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiUsageMapper {

    private final Clock clock;

    public AiUsageMapper(Clock clock) {
        this.clock = clock;
    }

    public AiRunUsage toSuccessfulUsage(
            AiRun run,
            AiRunStage stage,
            int requestIndex,
            long estimatedInputTokens,
            List<Content> promptContents,
            Result<?> result,
            long latencyMs,
            boolean googleSearchEnabled) {
        Map<String, Object> metadata = buildUsageMetadata(run, promptContents, googleSearchEnabled);
        metadata.put("toolExecutions", summarizeToolExecutions(result.toolExecutions()));
        metadata.put("intermediateResponses", result.intermediateResponses() != null ? result.intermediateResponses().size() : 0);

        TokenUsage tokenUsage = result.tokenUsage();
        return new AiRunUsage(
                null,
                run.id(),
                stage.id(),
                requestIndex,
                null,
                result.finishReason() != null ? result.finishReason().name() : null,
                estimatedInputTokens,
                tokenCountIn(tokenUsage),
                tokenCountOut(tokenUsage),
                tokenCountTotal(tokenUsage),
                result.toolExecutions() != null ? result.toolExecutions().size() : 0,
                countImages(promptContents),
                latencyMs,
                metadata,
                Instant.now(clock)
        );
    }

    public AiRunUsage toFailedUsage(
            AiRun run,
            AiRunStage stage,
            int requestIndex,
            long estimatedInputTokens,
            List<Content> promptContents,
            long latencyMs,
            Exception ex,
            boolean googleSearchEnabled,
            String errorSummary) {
        Map<String, Object> metadata = buildUsageMetadata(run, promptContents, googleSearchEnabled);
        metadata.put("errorType", ex.getClass().getSimpleName());
        metadata.put("errorSummary", errorSummary);
        metadata.put("countedAfterDispatch", true);

        return new AiRunUsage(
                null,
                run.id(),
                stage.id(),
                requestIndex,
                null,
                mapErrorFinishReason(ex),
                estimatedInputTokens,
                0,
                0,
                0,
                0,
                countImages(promptContents),
                latencyMs,
                metadata,
                Instant.now(clock)
        );
    }

    private Map<String, Object> buildUsageMetadata(
            AiRun run,
            List<Content> promptContents,
            boolean googleSearchEnabled) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", run.provider());
        metadata.put("model", run.model());
        metadata.put("runType", run.runType().name());
        metadata.put("promptContentCount", promptContents.size());
        metadata.put("googleSearchEnabled", googleSearchEnabled);
        return metadata;
    }

    private List<Map<String, Object>> summarizeToolExecutions(List<ToolExecution> toolExecutions) {
        if (toolExecutions == null || toolExecutions.isEmpty()) {
            return List.of();
        }
        return toolExecutions.stream().map(toolExecution -> {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("name", toolExecution.request() != null ? toolExecution.request().name() : null);
            summary.put("arguments", toolExecution.request() != null ? toolExecution.request().arguments() : null);
            summary.put("result", toolExecution.result());
            summary.put("failed", toolExecution.hasFailed());
            summary.put("durationMs", toolExecution.duration() != null ? toolExecution.duration().toMillis() : null);
            return summary;
        }).toList();
    }

    private int countImages(List<Content> promptContents) {
        return (int) promptContents.stream().filter(ImageContent.class::isInstance).count();
    }

    private long tokenCountIn(TokenUsage tokenUsage) {
        return tokenUsage != null && tokenUsage.inputTokenCount() != null ? tokenUsage.inputTokenCount() : 0;
    }

    private long tokenCountOut(TokenUsage tokenUsage) {
        return tokenUsage != null && tokenUsage.outputTokenCount() != null ? tokenUsage.outputTokenCount() : 0;
    }

    private long tokenCountTotal(TokenUsage tokenUsage) {
        return tokenUsage != null && tokenUsage.totalTokenCount() != null ? tokenUsage.totalTokenCount() : 0;
    }

    private String mapErrorFinishReason(Throwable throwable) {
        String simpleName = throwable.getClass().getSimpleName();
        String message = throwable.getMessage() != null ? throwable.getMessage() : "";

        if (simpleName.contains("Timeout")) {
            return "ERROR_TIMEOUT";
        }
        if (message.contains("\"code\": 429") || message.contains("RESOURCE_EXHAUSTED")) {
            return "ERROR_429";
        }
        if (message.contains("\"code\": 400") || simpleName.contains("InvalidRequest")) {
            return "ERROR_400";
        }
        return "ERROR_EXCEPTION";
    }
}
