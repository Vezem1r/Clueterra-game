package io.clueterra.questions.ai.service.generation;

import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.model.generation.AiGenerationBatchPlan;
import io.clueterra.questions.ai.model.generation.AiGenerationCountryGapCandidate;
import io.clueterra.questions.ai.model.run.AiRun;
import io.clueterra.questions.ai.model.run.AiRunPreparationResult;
import io.clueterra.questions.ai.model.run.AiRunStage;
import io.clueterra.questions.ai.model.run.AiRunStatus;
import io.clueterra.questions.ai.model.run.AiRunTriggerType;
import io.clueterra.questions.ai.model.run.AiRunType;
import io.clueterra.questions.ai.model.quota.AiGenerationQuotaSnapshot;
import io.clueterra.questions.ai.model.run.AiStageStatus;
import io.clueterra.questions.ai.model.run.AiStageType;
import io.clueterra.questions.ai.repository.AiGenerationGapRepository;
import io.clueterra.questions.ai.repository.AiRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiGenerationPreparationService {

    private static final Duration ABANDONED_PREPARED_RUN_TIMEOUT = Duration.ofMinutes(5);
    private static final int ESTIMATED_OUTPUT_TOKENS_PER_PROPOSAL = 220;

    private final AiProperties aiProperties;
    private final AiRunRepository runRepository;
    private final AiGenerationGapRepository generationGapRepository;
    private final AiGenerationBatchPlanner batchPlanner;
    private final AiGenerationQuotaService generationQuotaService;
    private final Clock clock;

    @Transactional
    public AiRunPreparationResult prepare(AiRunTriggerType triggerType, String requestedBy) {
        if (!aiProperties.isEnabled() || !aiProperties.getGeneration().isEnabled()) {
            log.info("AI generation prepare skipped: generation disabled");
            return AiRunPreparationResult.skipped(AiRunType.GENERATION, "AI generation is disabled");
        }
        expireStalePendingRuns();
        if (runRepository.hasPendingRun(AiRunType.GENERATION)) {
            log.warn("AI generation prepare skipped: pending run already exists");
            return AiRunPreparationResult.skipped(AiRunType.GENERATION, "A pending AI generation run already exists");
        }

        int fetchLimit = aiProperties.getGeneration().getCountryBatchSizeSoftCap()
                * aiProperties.getGeneration().getMaxRequestsPerRun()
                * 2;

        List<AiGenerationCountryGapCandidate> countries = generationGapRepository.findCountriesForGeneration(fetchLimit);

        if (countries.isEmpty()) {
            log.info("AI generation prepare found no countries for generation");
            AiRun noWorkRun = buildRun(triggerType, requestedBy, AiRunStatus.NO_WORK, 0, 0, 0, 0, "No active countries found for generation");
            AiRun saved = runRepository.save(noWorkRun);
            return new AiRunPreparationResult(saved.id(), AiRunType.GENERATION, saved.status(), 0, 0, 0, "No active countries found for generation");
        }

        AiGenerationQuotaSnapshot quotaSnapshot = generationQuotaService.getSnapshot();
        int maxRequestsByQuota = Math.min(
                aiProperties.getGeneration().getMaxRequestsPerRun(),
                quotaSnapshot.requestsTodayRemaining()
        );
        if (maxRequestsByQuota <= 0) {
            log.warn(
                    "AI generation prepare skipped by generation quota: pendingCountries={}, quota={}",
                    countries.size(),
                    generationQuotaService.summarizeSnapshot(quotaSnapshot)
            );
            AiRun skippedRun = buildRun(
                    triggerType,
                    requestedBy,
                    AiRunStatus.SKIPPED,
                    0,
                    0,
                    0,
                    0,
                    "Generation quota exhausted for the configured Gemini model"
            );
            AiRun saved = runRepository.save(skippedRun);
            return new AiRunPreparationResult(
                    saved.id(),
                    AiRunType.GENERATION,
                    saved.status(),
                    0,
                    0,
                    0,
                    "Generation quota exhausted for the configured Gemini model"
            );
        }

        List<AiGenerationBatchPlan> batches = batchPlanner.planBatches(countries).stream()
                .limit(maxRequestsByQuota)
                .toList();
        int preparedItems = batches.stream().mapToInt(AiGenerationBatchPlan::itemCount).sum();
        long estimatedInputTokens = batches.stream().mapToLong(AiGenerationBatchPlan::estimatedInputTokens).sum();
        long estimatedOutputTokens = batches.stream()
                .mapToLong(batch -> estimateOutputTokens(batch.countries()))
                .sum();
        List<String> preparedCountryCodes = batches.stream()
                .flatMap(batch -> batch.countries().stream())
                .map(AiGenerationCountryGapCandidate::countryCode)
                .distinct()
                .toList();

        AiRun preparedRun = buildRun(
                triggerType,
                requestedBy,
                AiRunStatus.PREPARED,
                batches.size(),
                preparedItems,
                estimatedInputTokens,
                estimatedOutputTokens,
                "Prepared AI generation run"
        );
        AiRun savedRun = runRepository.save(preparedRun);
        runRepository.saveStages(buildStages(savedRun.id(), batches));
        log.info(
                "AI generation run prepared: runId={}, stages={}, countries={}, estimatedInputTokens={}, countryCodes={}",
                savedRun.id(),
                batches.size(),
                preparedItems,
                estimatedInputTokens,
                preparedCountryCodes
        );

        return new AiRunPreparationResult(
                savedRun.id(),
                AiRunType.GENERATION,
                savedRun.status(),
                batches.size(),
                preparedItems,
                estimatedInputTokens,
                "Prepared AI generation run"
        );
    }

    private AiRun buildRun(
            AiRunTriggerType triggerType,
            String requestedBy,
            AiRunStatus status,
            int totalStages,
            int preparedItems,
            long estimatedInputTokens,
            long estimatedOutputTokens,
            String message) {

        Map<String, Object> configuration = buildRunConfiguration();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("message", message);

        Instant createdAt = Instant.now(clock);
        Instant completedAt = status == AiRunStatus.PREPARED ? null : createdAt;

        return new AiRun(
                null,
                AiRunType.GENERATION,
                triggerType,
                status,
                aiProperties.getProvider(),
                aiProperties.generationModelProfile().model(),
                aiProperties.getPromptVersion(),
                requestedBy,
                totalStages,
                preparedItems,
                0,
                0,
                0,
                estimatedInputTokens,
                estimatedOutputTokens,
                0,
                0,
                0,
                configuration,
                summary,
                null,
                createdAt,
                null,
                completedAt
        );
    }

    private List<AiRunStage> buildStages(Long runId, List<AiGenerationBatchPlan> batches) {
        List<AiRunStage> stages = new ArrayList<>();
        Instant createdAt = Instant.now(clock);
        for (AiGenerationBatchPlan batch : batches) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("countryIds", batch.countries().stream().map(AiGenerationCountryGapCandidate::countryId).toList());
            payload.put("countryCodes", batch.countries().stream().map(AiGenerationCountryGapCandidate::countryCode).toList());
            payload.put("requestedProposalsPerCountry", aiProperties.getGeneration().getRequestedProposalsPerCountry());
            payload.put("targetImageShare", aiProperties.getGeneration().getTargetImageShare());
            payload.put("targetLandmarkImageHintsPerCountry", aiProperties.getGeneration().getTargetLandmarkImageHintsPerCountry());
            payload.put("targetCurrencyImageHintsPerCountry", aiProperties.getGeneration().getTargetCurrencyImageHintsPerCountry());

            stages.add(new AiRunStage(
                    null,
                    runId,
                    batch.stageIndex(),
                    AiStageType.GENERATION_BATCH,
                    AiStageStatus.PREPARED,
                    batch.itemCount(),
                    batch.estimatedInputTokens(),
                    estimateOutputTokens(batch.countries()),
                    0,
                    0,
                    0,
                    payload,
                    null,
                    null,
                    null,
                    createdAt,
                    null,
                    null
            ));
        }
        return stages;
    }

    private Map<String, Object> buildRunConfiguration() {
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("countryBatchSoftCap", aiProperties.getGeneration().getCountryBatchSizeSoftCap());
        configuration.put("maxRequestsPerRun", aiProperties.getGeneration().getMaxRequestsPerRun());
        configuration.put("providerRetryBackoffMillis", aiProperties.getProviderRetry().getBackoffMillis());
        configuration.put("requestedProposalsPerCountry", aiProperties.getGeneration().getRequestedProposalsPerCountry());
        configuration.put("thinkingLevel", aiProperties.getGeneration().getThinkingLevel());
        configuration.put("maxEmbeddingRepairPasses", aiProperties.getGeneration().getMaxEmbeddingRepairPasses());
        configuration.put("targetImageShare", aiProperties.getGeneration().getTargetImageShare());
        configuration.put("specializedImageSearchAttempts", aiProperties.getGeneration().getSpecializedImageSearchAttempts());
        configuration.put("targetLandmarkImageHintsPerCountry", aiProperties.getGeneration().getTargetLandmarkImageHintsPerCountry());
        configuration.put("targetCurrencyImageHintsPerCountry", aiProperties.getGeneration().getTargetCurrencyImageHintsPerCountry());
        configuration.put("specializedImageRetryCooldownDays", aiProperties.getGeneration().getSpecializedImageRetryCooldownDays());
        configuration.put("requestsPerMinuteLimit", aiProperties.getGeneration().getQuota().getRequestsPerMinute());
        configuration.put("inputTokensPerMinuteLimit", aiProperties.getGeneration().getQuota().getInputTokensPerMinute());
        configuration.put("requestsPerDayLimit", aiProperties.getGeneration().getQuota().getRequestsPerDay());
        return configuration;
    }

    private long estimateOutputTokens(List<AiGenerationCountryGapCandidate> countries) {
        return countries.stream()
                .mapToLong(this::estimateProposalCount)
                .sum() * ESTIMATED_OUTPUT_TOKENS_PER_PROPOSAL;
    }

    private int estimateProposalCount(AiGenerationCountryGapCandidate country) {
        return aiProperties.getGeneration().getRequestedProposalsPerCountry();
    }

    private void expireStalePendingRuns() {
        if (!aiProperties.getRecovery().isAutoCancelStalePendingRuns()) {
            return;
        }

        int abandonedCancelled = runRepository.cancelAbandonedPreparedRuns(
                AiRunType.GENERATION,
                Instant.now(clock).minus(ABANDONED_PREPARED_RUN_TIMEOUT),
                "Automatically cancelled abandoned AI generation run before preparing a new one"
        );
        if (abandonedCancelled > 0) {
            log.warn("AI generation recovery cancelled {} abandoned pending run(s) before prepare", abandonedCancelled);
        }
        Instant cutoff = Instant.now(clock).minus(Duration.ofMinutes(
                aiProperties.getRecovery().getStalePendingRunMinutes()));
        int staleCancelled = runRepository.cancelStalePendingRuns(
                AiRunType.GENERATION,
                cutoff,
                "Automatically cancelled stale AI generation run before preparing a new one"
        );
        if (staleCancelled > 0) {
            log.warn("AI generation recovery cancelled {} stale pending run(s) before prepare", staleCancelled);
        }
    }
}
