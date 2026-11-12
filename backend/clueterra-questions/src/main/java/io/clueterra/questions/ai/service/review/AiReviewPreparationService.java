package io.clueterra.questions.ai.service.review;

import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.model.run.AiRun;
import io.clueterra.questions.ai.model.run.AiRunPreparationResult;
import io.clueterra.questions.ai.model.run.AiRunStage;
import io.clueterra.questions.ai.model.run.AiRunStatus;
import io.clueterra.questions.ai.model.run.AiRunTriggerType;
import io.clueterra.questions.ai.model.run.AiRunType;
import io.clueterra.questions.ai.model.review.AiReviewBatchPlan;
import io.clueterra.questions.ai.model.review.AiReviewCandidate;
import io.clueterra.questions.ai.model.quota.AiReviewQuotaSnapshot;
import io.clueterra.questions.ai.model.run.AiStageStatus;
import io.clueterra.questions.ai.model.run.AiStageType;
import io.clueterra.questions.ai.repository.AiReviewQueueRepository;
import io.clueterra.questions.ai.repository.AiRunRepository;
import io.clueterra.questions.ai.service.support.AiTokenEstimator;
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
public class AiReviewPreparationService {

    private static final Duration ABANDONED_PREPARED_RUN_TIMEOUT = Duration.ofMinutes(5);
    private static final int ESTIMATED_OUTPUT_TOKENS_PER_HINT = 180;

    private final AiProperties aiProperties;
    private final AiRunRepository runRepository;
    private final AiReviewQueueRepository reviewQueueRepository;
    private final AiReviewBatchPlanner batchPlanner;
    private final AiReviewQuotaService reviewQuotaService;
    private final AiTokenEstimator tokenEstimator;
    private final Clock clock;

    @Transactional
    public AiRunPreparationResult prepare(AiRunTriggerType triggerType, String requestedBy) {
        if (!aiProperties.isEnabled() || !aiProperties.getReview().isEnabled()) {
            log.info("AI review prepare skipped: review disabled");
            return AiRunPreparationResult.skipped(AiRunType.REVIEW, "AI review is disabled");
        }
        expireStalePendingRuns();
        if (runRepository.hasPendingRun(AiRunType.REVIEW)) {
            log.warn("AI review prepare skipped: pending run already exists");
            return AiRunPreparationResult.skipped(AiRunType.REVIEW, "A pending AI review run already exists");
        }

        AiReviewQuotaSnapshot quotaSnapshot = reviewQuotaService.getSnapshot();
        int maxRequestsByQuota = Math.min(
                aiProperties.getReview().getMaxRequestsPerRun(),
                quotaSnapshot.googleSearchEnabled() && quotaSnapshot.groundedPromptsTodayRemaining() != null
                        ? Math.min(quotaSnapshot.requestsTodayRemaining(), quotaSnapshot.groundedPromptsTodayRemaining())
                        : quotaSnapshot.requestsTodayRemaining()
        );
        if (maxRequestsByQuota <= 0) {
            log.warn(
                    "AI review prepare skipped by reviewer quota: pendingCandidates={}, quota={}",
                    0,
                    reviewQuotaService.summarizeSnapshot(quotaSnapshot)
            );
            AiRun skippedRun = buildRun(
                    null,
                    triggerType,
                    requestedBy,
                    AiRunStatus.SKIPPED,
                    0,
                    0,
                    0,
                    0,
                    "Reviewer quota exhausted for the configured Gemini model"
            );
            AiRun saved = runRepository.save(skippedRun);
            return new AiRunPreparationResult(
                    saved.id(),
                    AiRunType.REVIEW,
                    saved.status(),
                    0,
                    0,
                    0,
                    "Reviewer quota exhausted for the configured Gemini model"
            );
        }

        AiRun claimRun = runRepository.save(buildRun(
                null,
                triggerType,
                requestedBy,
                AiRunStatus.PREPARED,
                0,
                0,
                0,
                0,
                "Claiming AI review candidates"
        ));
        int fetchLimit = aiProperties.getReview().getDefaultBatchSizeSoftCap() * maxRequestsByQuota;
        List<AiReviewCandidate> candidates = reviewQueueRepository.claimPendingCandidates(
                claimRun.id(),
                fetchLimit
        );
        if (candidates.isEmpty()) {
            log.info("AI review prepare found no pending candidates");
            AiRun saved = runRepository.save(buildRun(
                    claimRun.id(), triggerType, requestedBy, AiRunStatus.NO_WORK,
                    0, 0, 0, 0, "No hints pending review"));
            return new AiRunPreparationResult(saved.id(), AiRunType.REVIEW, saved.status(), 0, 0, 0, "No hints pending review");
        }

        List<AiReviewBatchPlan> batches = batchPlanner.planBatches(candidates, maxRequestsByQuota);
        int preparedItems = batches.stream().mapToInt(AiReviewBatchPlan::itemCount).sum();
        long estimatedInputTokens = batches.stream().mapToLong(AiReviewBatchPlan::estimatedInputTokens).sum();
        long estimatedOutputTokens = estimateOutputTokens(preparedItems);

        AiRun preparedRun = buildRun(
                claimRun.id(),
                triggerType,
                requestedBy,
                AiRunStatus.PREPARED,
                batches.size(),
                preparedItems,
                estimatedInputTokens,
                estimatedOutputTokens,
                "Prepared AI review run"
        );
        AiRun savedRun = runRepository.save(preparedRun);
        runRepository.saveStages(buildStages(savedRun.id(), batches));
        log.info(
                "AI review run prepared: runId={}, stages={}, items={}, estimatedInputTokens={}, countryCodes={}",
                savedRun.id(),
                batches.size(),
                preparedItems,
                estimatedInputTokens,
                candidates.stream().map(AiReviewCandidate::countryCode).distinct().toList()
        );

        return new AiRunPreparationResult(
                savedRun.id(),
                AiRunType.REVIEW,
                savedRun.status(),
                batches.size(),
                preparedItems,
                estimatedInputTokens,
                "Prepared AI review run"
        );
    }

    private AiRun buildRun(
            Long runId,
            AiRunTriggerType triggerType,
            String requestedBy,
            AiRunStatus status,
            int totalStages,
            int preparedItems,
            long estimatedInputTokens,
            long estimatedOutputTokens,
            String message) {

        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("batchSoftCap", aiProperties.getReview().getDefaultBatchSizeSoftCap());
        configuration.put("maxRequestsPerRun", aiProperties.getReview().getMaxRequestsPerRun());
        configuration.put("temperature", aiProperties.getReview().getTemperature());
        configuration.put("providerRetryBackoffMillis", aiProperties.getProviderRetry().getBackoffMillis());
        configuration.put("targetInputTokensPerRequest", tokenEstimator.reviewTargetInputTokensPerRequest());
        configuration.put("requestsPerMinuteLimit", aiProperties.getReview().getQuota().getRequestsPerMinute());
        configuration.put("inputTokensPerMinuteLimit", aiProperties.getReview().getQuota().getInputTokensPerMinute());
        configuration.put("requestsPerDayLimit", aiProperties.getReview().getQuota().getRequestsPerDay());
        configuration.put("groundedPromptsPerDayLimit", aiProperties.getReview().getQuota().getGroundedPromptsPerDay());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("message", message);

        Instant createdAt = Instant.now(clock);
        Instant completedAt = status == AiRunStatus.PREPARED ? null : createdAt;

        return new AiRun(
                runId,
                AiRunType.REVIEW,
                triggerType,
                status,
                aiProperties.getProvider(),
                aiProperties.reviewModelProfile().model(),
                aiProperties.getReview().getPromptVersion(),
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

    private List<AiRunStage> buildStages(Long runId, List<AiReviewBatchPlan> batches) {
        List<AiRunStage> stages = new ArrayList<>();
        Instant createdAt = Instant.now(clock);
        for (AiReviewBatchPlan batch : batches) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("hintIds", batch.candidates().stream().map(AiReviewCandidate::hintId).toList());
            payload.put("countryCodes", batch.candidates().stream().map(AiReviewCandidate::countryCode).distinct().toList());
            payload.put("imageCount", batch.imageCount());

            stages.add(new AiRunStage(
                    null,
                    runId,
                    batch.stageIndex(),
                    AiStageType.REVIEW_BATCH,
                    AiStageStatus.PREPARED,
                    batch.itemCount(),
                    batch.estimatedInputTokens(),
                    estimateOutputTokens(batch.itemCount()),
                    0,
                    0,
                    batch.imageCount(),
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

    private long estimateOutputTokens(int itemCount) {
        return (long) itemCount * ESTIMATED_OUTPUT_TOKENS_PER_HINT;
    }

    private void expireStalePendingRuns() {
        if (aiProperties.getRecovery().isAutoCancelStalePendingRuns()) {
            int abandonedCancelled = runRepository.cancelAbandonedPreparedRuns(
                    AiRunType.REVIEW,
                    Instant.now(clock).minus(ABANDONED_PREPARED_RUN_TIMEOUT),
                    "Automatically cancelled abandoned AI review run before preparing a new one"
            );
            if (abandonedCancelled > 0) {
                log.warn("AI review recovery cancelled {} abandoned pending run(s) before prepare", abandonedCancelled);
            }
            Instant cutoff = Instant.now(clock).minus(Duration.ofMinutes(
                    aiProperties.getRecovery().getStalePendingRunMinutes()));
            int staleCancelled = runRepository.cancelStalePendingRuns(
                    AiRunType.REVIEW,
                    cutoff,
                    "Automatically cancelled stale AI review run before preparing a new one"
            );
            if (staleCancelled > 0) {
                log.warn("AI review recovery cancelled {} stale pending run(s) before prepare", staleCancelled);
            }
        }
        int releasedClaims = reviewQueueRepository.releaseInactiveRunClaims();
        if (releasedClaims > 0) {
            log.warn("AI review recovery released {} orphaned hint claim(s)", releasedClaims);
        }
    }
}
