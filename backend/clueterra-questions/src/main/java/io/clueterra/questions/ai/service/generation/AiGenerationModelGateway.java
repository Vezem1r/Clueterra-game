package io.clueterra.questions.ai.service.generation;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.service.Result;
import io.clueterra.questions.ai.assistant.AiGenerationImageValidationAssistant;
import io.clueterra.questions.ai.assistant.AiHintGenerationAssistant;
import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.model.dispatch.AiProviderDispatchRequest;
import io.clueterra.questions.ai.model.quota.AiGenerationQuotaSnapshot;
import io.clueterra.questions.ai.model.run.AiRun;
import io.clueterra.questions.ai.model.run.AiRunStage;
import io.clueterra.questions.ai.service.support.AiProviderDispatcher;
import io.clueterra.questions.ai.service.support.AiStageExecutionSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Component
@ConditionalOnProperty(prefix = "clueterra.ai", name = "enabled", havingValue = "true")
public class AiGenerationModelGateway {

    private final AiProperties aiProperties;
    private final AiHintGenerationAssistant hintGenerationAssistant;
    private final AiGenerationImageValidationAssistant imageValidationAssistant;
    private final AiGenerationQuotaService generationQuotaService;
    private final AiProviderDispatcher providerDispatcher;
    private final AiStageExecutionSupport stageExecutionSupport;

    public AiGenerationModelGateway(
            AiProperties aiProperties,
            AiHintGenerationAssistant hintGenerationAssistant,
            AiGenerationImageValidationAssistant imageValidationAssistant,
            AiGenerationQuotaService generationQuotaService,
            AiProviderDispatcher providerDispatcher,
            AiStageExecutionSupport stageExecutionSupport) {
        this.aiProperties = aiProperties;
        this.hintGenerationAssistant = hintGenerationAssistant;
        this.imageValidationAssistant = imageValidationAssistant;
        this.generationQuotaService = generationQuotaService;
        this.providerDispatcher = providerDispatcher;
        this.stageExecutionSupport = stageExecutionSupport;
    }

    public Result<String> generate(
            AiRun run,
            AiRunStage stage,
            AtomicInteger requestIndex,
            List<Content> promptContents,
            AtomicLong dispatchedInputTokens,
            AtomicLong dispatchedOutputTokens,
            AtomicInteger dispatchedToolCalls) {
        return dispatch(
                run,
                stage,
                requestIndex,
                promptContents,
                dispatchedInputTokens,
                dispatchedOutputTokens,
                dispatchedToolCalls,
                () -> hintGenerationAssistant.generate(promptContents)
        );
    }

    public Result<String> validateImages(
            AiRun run,
            AiRunStage stage,
            AtomicInteger requestIndex,
            List<Content> promptContents,
            AtomicLong dispatchedInputTokens,
            AtomicLong dispatchedOutputTokens,
            AtomicInteger dispatchedToolCalls) {
        return dispatch(
                run,
                stage,
                requestIndex,
                promptContents,
                dispatchedInputTokens,
                dispatchedOutputTokens,
                dispatchedToolCalls,
                () -> imageValidationAssistant.validate(promptContents)
        );
    }

    private Result<String> dispatch(
            AiRun run,
            AiRunStage stage,
            AtomicInteger requestIndex,
            List<Content> promptContents,
            AtomicLong dispatchedInputTokens,
            AtomicLong dispatchedOutputTokens,
            AtomicInteger dispatchedToolCalls,
            Supplier<Result<String>> providerCall) {
        Result<String> result = providerDispatcher.dispatch(new AiProviderDispatchRequest(
                run,
                stage,
                requestIndex,
                promptContents,
                aiProperties.getGeneration().isCountFailedRequestsAfterDispatch(),
                false,
                () -> generationQuotaService.awaitMinuteCapacity(stage.estimatedInputTokens()),
                this::hasDailyRetryCapacity,
                providerCall
        ));
        dispatchedInputTokens.addAndGet(stageExecutionSupport.tokenCountIn(result.tokenUsage()));
        dispatchedOutputTokens.addAndGet(stageExecutionSupport.tokenCountOut(result.tokenUsage()));
        dispatchedToolCalls.addAndGet(result.toolExecutions() != null ? result.toolExecutions().size() : 0);
        return result;
    }

    private boolean hasDailyRetryCapacity() {
        AiGenerationQuotaSnapshot snapshot = generationQuotaService.getSnapshot();
        return snapshot.requestsTodayRemaining() > 0;
    }
}
