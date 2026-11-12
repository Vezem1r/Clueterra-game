package io.clueterra.questions.ai.service.run;

import io.clueterra.questions.ai.mapper.AiRunMapper;
import io.clueterra.questions.ai.mapper.AiStageMapper;
import io.clueterra.questions.ai.model.changeset.AiChangeSetStatus;
import io.clueterra.questions.ai.model.quota.AiGenerationQuotaSnapshot;
import io.clueterra.questions.ai.model.quota.AiReviewQuotaSnapshot;
import io.clueterra.questions.ai.model.run.AiRun;
import io.clueterra.questions.ai.model.run.AiRunStage;
import io.clueterra.questions.ai.model.run.AiRunStatus;
import io.clueterra.questions.ai.model.run.AiRunType;
import io.clueterra.questions.ai.model.run.AiStageExecutionResult;
import io.clueterra.questions.ai.model.run.AiStageStatus;
import io.clueterra.questions.ai.repository.AiExecutionRepository;
import io.clueterra.questions.ai.repository.AiReviewQueueRepository;
import io.clueterra.questions.ai.repository.AiRunRepository;
import io.clueterra.questions.ai.service.change.AiChangeSetService;
import io.clueterra.questions.ai.service.generation.AiGenerationQuotaService;
import io.clueterra.questions.ai.service.generation.AiGenerationStageExecutor;
import io.clueterra.questions.ai.service.review.AiReviewQuotaService;
import io.clueterra.questions.ai.service.review.AiReviewStageExecutor;
import io.clueterra.questions.ai.service.support.AiStageExecutionSupport;
import io.clueterra.questions.config.AppSettingsReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.clueterra.questions.exception.ResourceNotFoundException;

@Service
@Slf4j
@ConditionalOnProperty(prefix = "clueterra.ai", name = "enabled", havingValue = "true")
public class AiRunExecutionService {

    private final AiRunRepository runRepository;
    private final AiExecutionRepository executionRepository;
    private final AiReviewQueueRepository reviewQueueRepository;
    private final AiReviewQuotaService reviewQuotaService;
    private final AiGenerationQuotaService generationQuotaService;
    private final AiRunMapper runMapper;
    private final AiStageMapper stageMapper;
    private final AiStageExecutionSupport stageExecutionSupport;
    private final AiReviewStageExecutor reviewStageExecutor;
    private final AiGenerationStageExecutor generationStageExecutor;
    private final AiChangeSetService changeSetService;
    private final AppSettingsReader appSettingsReader;

    public AiRunExecutionService(
            AiRunRepository runRepository,
            AiExecutionRepository executionRepository,
            AiReviewQueueRepository reviewQueueRepository,
            AiReviewQuotaService reviewQuotaService,
            AiGenerationQuotaService generationQuotaService,
            AiRunMapper runMapper,
            AiStageMapper stageMapper,
            AiStageExecutionSupport stageExecutionSupport,
            AiReviewStageExecutor reviewStageExecutor,
            AiGenerationStageExecutor generationStageExecutor,
            AiChangeSetService changeSetService,
            AppSettingsReader appSettingsReader) {
        this.runRepository = runRepository;
        this.executionRepository = executionRepository;
        this.reviewQueueRepository = reviewQueueRepository;
        this.reviewQuotaService = reviewQuotaService;
        this.generationQuotaService = generationQuotaService;
        this.runMapper = runMapper;
        this.stageMapper = stageMapper;
        this.stageExecutionSupport = stageExecutionSupport;
        this.reviewStageExecutor = reviewStageExecutor;
        this.generationStageExecutor = generationStageExecutor;
        this.changeSetService = changeSetService;
        this.appSettingsReader = appSettingsReader;
    }

    @Job(name = "AI Run Execution", retries = 2)
    public void executeRun(Long runId) {
        AiRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("AI run not found: " + runId));
        if (run.status() != AiRunStatus.PREPARED && run.status() != AiRunStatus.RUNNING) {
            return;
        }

        AiRun runningRun = runRepository.save(runMapper.toRunning(run));
        log.info(
                "AI run started: runId={}, runType={}, model={}, requestedBy={}, stages={}, preparedItems={}, estimatedInputTokens={}, estimatedOutputTokens={}",
                runningRun.id(),
                runningRun.runType(),
                runningRun.model(),
                runningRun.requestedBy(),
                runningRun.totalStages(),
                runningRun.preparedItems(),
                runningRun.estimatedInputTokens(),
                runningRun.estimatedOutputTokens()
        );

        List<AiRunStage> stages = runRepository.findStagesByRunId(runId);
        int processedItems = 0;
        int changedItems = 0;
        int generatedItems = 0;
        long actualInputTokens = 0;
        long actualOutputTokens = 0;
        int toolCallCount = 0;
        boolean anyStageProcessed = false;

        try {
            for (AiRunStage stage : stages) {
                if (stage.status() != AiStageStatus.PREPARED) {
                    continue;
                }

                if (!canDispatchStage(runningRun, stage)) {
                    continue;
                }

                AiRunStage runningStage = saveStage(stageMapper.toRunning(stage));
                log.info(
                        "AI stage started: runId={}, stageId={}, stageIndex={}, stageType={}, itemCount={}, estimatedInputTokens={}, estimatedOutputTokens={}, scope={}",
                        runningRun.id(),
                        runningStage.id(),
                        runningStage.stageIndex(),
                        runningStage.stageType(),
                        runningStage.itemCount(),
                        runningStage.estimatedInputTokens(),
                        runningStage.estimatedOutputTokens(),
                        summarizeStageScope(runningStage)
                );

                AiStageExecutionResult result;
                try {
                    result = executeStage(runningRun, runningStage);
                } catch (Exception ex) {
                    String errorSummary = stageExecutionSupport.summarizeException(ex);
                    saveStage(stageMapper.toFailed(runningStage, errorSummary));
                    if (isExpectedProviderError(ex)) {
                        log.warn(
                                "AI stage failed (provider error): runId={}, stageId={}, stageIndex={}, stageType={}, scope={}, quota={}, error={}",
                                runningRun.id(),
                                runningStage.id(),
                                runningStage.stageIndex(),
                                runningStage.stageType(),
                                summarizeStageScope(runningStage),
                                summarizeQuotaContext(runningRun.runType()),
                                errorSummary
                        );
                    } else {
                        log.error(
                                "AI stage failed: runId={}, stageId={}, stageIndex={}, stageType={}, scope={}, quota={}, error={}",
                                runningRun.id(),
                                runningStage.id(),
                                runningStage.stageIndex(),
                                runningStage.stageType(),
                                summarizeStageScope(runningStage),
                                summarizeQuotaContext(runningRun.runType()),
                                errorSummary,
                                ex
                        );
                    }
                    throw ex;
                }

                anyStageProcessed = anyStageProcessed || result.processedItems() > 0 || result.generatedItems() > 0;
                processedItems += result.processedItems();
                changedItems += result.changedItems();
                generatedItems += result.generatedItems();
                actualInputTokens += result.actualInputTokens();
                actualOutputTokens += result.actualOutputTokens();
                toolCallCount += result.toolCallCount();

                saveStage(stageMapper.toCompleted(runningStage, result));
                log.info(
                        "AI stage completed: runId={}, stageId={}, stageIndex={}, processedItems={}, changedItems={}, generatedItems={}, actualInputTokens={}, actualOutputTokens={}, toolCalls={}, scope={}",
                        runningRun.id(),
                        runningStage.id(),
                        runningStage.stageIndex(),
                        result.processedItems(),
                        result.changedItems(),
                        result.generatedItems(),
                        result.actualInputTokens(),
                        result.actualOutputTokens(),
                        result.toolCallCount(),
                        summarizeStageScope(runningStage)
                );
            }

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("processedItems", processedItems);
            summary.put("changedItems", changedItems);
            summary.put("generatedItems", generatedItems);

            runRepository.save(anyStageProcessed
                    ? runMapper.toCompleted(
                    runningRun,
                    processedItems,
                    changedItems,
                    generatedItems,
                    actualInputTokens,
                    actualOutputTokens,
                    toolCallCount,
                    summary
            )
                    : runMapper.toSkipped(
                    runningRun,
                    processedItems,
                    changedItems,
                    generatedItems,
                    actualInputTokens,
                    actualOutputTokens,
                    toolCallCount,
                    summary
            ));
            log.info(
                    "AI run completed: runId={}, status={}, processedItems={}, changedItems={}, generatedItems={}, actualInputTokens={}, actualOutputTokens={}, toolCalls={}",
                    runningRun.id(),
                    anyStageProcessed ? AiRunStatus.COMPLETED : AiRunStatus.SKIPPED,
                    processedItems,
                    changedItems,
                    generatedItems,
                    actualInputTokens,
                    actualOutputTokens,
                    toolCallCount
            );

            if (anyStageProcessed) {
                tryAutoApply(runningRun);
            }
        } catch (Exception ex) {
            String errorSummary = stageExecutionSupport.summarizeException(ex);
            boolean expectedProviderError = isExpectedProviderError(ex);
            runRepository.save(runMapper.toFailed(
                    runningRun,
                    processedItems,
                    changedItems,
                    generatedItems,
                    actualInputTokens,
                    actualOutputTokens,
                    toolCallCount,
                    errorSummary
            ));
            if (anyStageProcessed) {
                tryAutoApply(runningRun);
            }
            if (expectedProviderError) {
                log.warn(
                        "AI run failed (provider error): runId={}, runType={}, processedItems={}, changedItems={}, generatedItems={}, quota={}, error={}",
                        runningRun.id(),
                        runningRun.runType(),
                        processedItems,
                        changedItems,
                        generatedItems,
                        summarizeQuotaContext(runningRun.runType()),
                        errorSummary
                );
            } else {
                log.error(
                        "AI run failed: runId={}, runType={}, processedItems={}, changedItems={}, generatedItems={}, quota={}, error={}",
                        runningRun.id(),
                        runningRun.runType(),
                        processedItems,
                        changedItems,
                        generatedItems,
                        summarizeQuotaContext(runningRun.runType()),
                        errorSummary,
                        ex
                );
                throw ex;
            }
        } finally {
            if (runningRun.runType() == AiRunType.REVIEW) {
                int releasedClaims = reviewQueueRepository.releaseRunClaims(runningRun.id());
                if (releasedClaims > 0) {
                    log.warn("Released {} unfinished hint claim(s) after review run {}", releasedClaims, runningRun.id());
                }
            }
        }
    }

    private void tryAutoApply(AiRun run) {
        boolean autoApprove = run.runType() == AiRunType.REVIEW ? appSettingsReader.isAutoApproveReview() : appSettingsReader.isAutoApproveGeneration();
        if (!autoApprove) {
            return;
        }

        try {
            executionRepository.findChangeSetByRunId(run.id())
                    .filter(cs -> cs.status() == AiChangeSetStatus.DRAFT)
                    .ifPresent(cs -> {
                        if (run.runType() == AiRunType.REVIEW) {
                            changeSetService.applySafeReviewUpdates(cs.id());
                        } else {
                            changeSetService.apply(cs.id());
                        }
                        log.info("Auto-applied eligible change items: runId={}, changeSetId={}, runType={}", run.id(), cs.id(), run.runType());
                    });
        } catch (Exception ex) {
            log.error("Failed to auto-apply change set: runId={}, runType={}, error={}", run.id(), run.runType(), ex.getMessage(), ex);
        }
    }

    private boolean canDispatchStage(AiRun run, AiRunStage stage) {
        if (run.runType() == AiRunType.REVIEW) {
            AiReviewQuotaSnapshot quotaSnapshot = reviewQuotaService.getSnapshot();
            if (canDispatchReviewStage(stage, quotaSnapshot)) {
                return true;
            }
            log.warn(
                    "AI review stage skipped by reviewer quota: runId={}, stageId={}, stageIndex={}, stageType={}, itemCount={}, scope={}, quota={}",
                    run.id(),
                    stage.id(),
                    stage.stageIndex(),
                    stage.stageType(),
                    stage.itemCount(),
                    summarizeStageScope(stage),
                    reviewQuotaService.summarizeSnapshot(quotaSnapshot)
            );
            saveStage(stageMapper.toSkipped(stage, "Reviewer quota exhausted before stage execution"));
            return false;
        }

        AiGenerationQuotaSnapshot quotaSnapshot = generationQuotaService.getSnapshot();
        if (quotaSnapshot.requestsTodayRemaining() >= 1
                && stage.estimatedInputTokens() <= quotaSnapshot.inputTokensPerMinuteLimit()) {
            if (quotaSnapshot.requestsLastMinuteRemaining() < 1
                    || quotaSnapshot.inputTokensLastMinuteRemaining() < stage.estimatedInputTokens()) {
                log.info(
                        "AI generation stage is waiting for temporary minute quota: runId={}, stageId={}, stageIndex={}, scope={}, quota={}",
                        run.id(),
                        stage.id(),
                        stage.stageIndex(),
                        summarizeStageScope(stage),
                        generationQuotaService.summarizeSnapshot(quotaSnapshot));
            }
            generationQuotaService.awaitMinuteCapacity(stage.estimatedInputTokens());
            return true;
        }
        log.warn(
                "AI generation stage skipped by generation quota: runId={}, stageId={}, stageIndex={}, stageType={}, itemCount={}, scope={}, quota={}",
                run.id(),
                stage.id(),
                stage.stageIndex(),
                stage.stageType(),
                stage.itemCount(),
                summarizeStageScope(stage),
                generationQuotaService.summarizeSnapshot(quotaSnapshot)
        );
        saveStage(stageMapper.toSkipped(stage, "Generation quota exhausted before stage execution"));
        return false;
    }

    private AiStageExecutionResult executeStage(AiRun run, AiRunStage stage) {
        return run.runType() == AiRunType.REVIEW
                ? reviewStageExecutor.execute(run, stage)
                : generationStageExecutor.execute(run, stage);
    }

    private AiRunStage saveStage(AiRunStage stage) {
        return runRepository.saveStages(List.of(stage)).getFirst();
    }

    private List<String> extractStringList(Map<String, Object> payload) {
        Object value = payload != null ? payload.get("countryCodes") : null;
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(String::valueOf)
                .filter(StringUtils::isNotBlank)
                .toList();
    }

    private String summarizeStageScope(AiRunStage stage) {
        List<String> countryCodes = extractStringList(stage.payload());
        List<Long> hintIds = stageExecutionSupport.extractLongList(stage.payload(), "hintIds");
        List<Long> countryIds = stageExecutionSupport.extractLongList(stage.payload(), "countryIds");

        List<String> parts = new ArrayList<>();
        if (!countryCodes.isEmpty()) {
            parts.add("countryCodes=" + countryCodes);
        }
        if (!countryIds.isEmpty()) {
            parts.add("countryIds=" + countryIds);
        }
        if (!hintIds.isEmpty()) {
            parts.add("hintIds=" + (hintIds.size() > 8 ? hintIds.subList(0, 8) + "...(" + hintIds.size() + ")" : hintIds.toString()));
        }
        Object payloadKeys = stage.payload() != null ? stage.payload().keySet() : List.of();
        return parts.isEmpty() ? "payloadKeys=" + payloadKeys : String.join(", ", parts);
    }

    private String summarizeQuotaContext(AiRunType runType) {
        if (runType == AiRunType.REVIEW) {
            return reviewQuotaService.summarizeSnapshot(reviewQuotaService.getSnapshot());
        }
        return generationQuotaService.summarizeSnapshot(generationQuotaService.getSnapshot());
    }

    private boolean canDispatchReviewStage(AiRunStage stage, AiReviewQuotaSnapshot snapshot) {
        if (snapshot.requestsLastMinuteRemaining() < 1) {
            return false;
        }
        if (snapshot.inputTokensLastMinuteRemaining() < stage.estimatedInputTokens()) {
            return false;
        }
        if (snapshot.requestsTodayRemaining() < 1) {
            return false;
        }
        return !snapshot.googleSearchEnabled()
                || snapshot.groundedPromptsTodayRemaining() == null
                || snapshot.groundedPromptsTodayRemaining() >= 1;
    }

    private boolean isExpectedProviderError(Exception ex) {
        String summary = stageExecutionSupport.summarizeException(ex);
        return summary.contains("\"code\": 503")
                || summary.contains("\"code\": 429")
                || summary.contains("UNAVAILABLE")
                || summary.contains("RESOURCE_EXHAUSTED")
                || summary.contains("rate limit")
                || summary.contains("Too Many Requests")
                || summary.contains("quota exceeded")
                || summary.contains("currently experiencing high demand")
                || summary.contains("exceeded") && summary.contains("tool invocations")
                || ex instanceof dev.langchain4j.exception.InternalServerException
                || ex instanceof dev.langchain4j.exception.RateLimitException;
    }
}
