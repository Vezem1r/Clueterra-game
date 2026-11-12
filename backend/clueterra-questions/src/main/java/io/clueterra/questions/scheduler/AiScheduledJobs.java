package io.clueterra.questions.scheduler;

import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.model.run.AiRun;
import io.clueterra.questions.ai.model.run.AiRunPreparationResult;
import io.clueterra.questions.ai.model.run.AiRunTriggerType;
import io.clueterra.questions.ai.service.embedding.AiHintEmbeddingService;
import io.clueterra.questions.ai.service.run.AiRunLaunchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.Optional;

@Configuration
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "clueterra.ai", name = "enabled", havingValue = "true")
public class AiScheduledJobs {

    static final String REVIEW_JOB_ID = "ai-review-prepare";
    static final String GENERATION_JOB_ID = "ai-generation-prepare";
    static final String EMBEDDING_JOB_ID = "ai-embedding-backfill";

    private final JobScheduler jobScheduler;
    private final AiProperties aiProperties;
    private final AiRunLaunchService runLaunchService;
    private final Optional<AiHintEmbeddingService> embeddingService;

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleJobs() {
        jobScheduler.scheduleRecurrently(REVIEW_JOB_ID, aiProperties.getReview().getCron(), this::prepareReviewRunAction);
        jobScheduler.scheduleRecurrently(GENERATION_JOB_ID, aiProperties.getGeneration().getCron(), this::prepareGenerationRunAction);
        if (embeddingService.isPresent()) {
            jobScheduler.scheduleRecurrently(EMBEDDING_JOB_ID, aiProperties.getEmbedding().getCron(), this::backfillEmbeddingsAction);
        } else {
            jobScheduler.deleteRecurringJob(EMBEDDING_JOB_ID);
        }
    }

    @Job(name = "Local Hint Embedding Backfill")
    public void backfillEmbeddingsAction() {
        embeddingService.ifPresent(service -> {
            if (service.coverage().missingHints() == 0) {
                log.debug("Local embedding backfill skipped: all active hints are covered");
                return;
            }
            if (!service.isAvailable()) {
                log.warn("Local embedding backfill skipped: embedding service is unavailable");
                return;
            }
            AiRun run = service.backfillPending(AiRunTriggerType.SCHEDULED, "scheduler");
            log.info("Local embedding backfill result: status={}, runId={}, stored={}",
                    run.status(), run.id(), run.changedItems());
        });
    }

    @Job(name = "AI Review Preparation")
    public void prepareReviewRunAction() {
        if (!aiProperties.getReview().isEnabled() || !aiProperties.getReview().isSchedulerEnabled()) {
            return;
        }
        AiRunPreparationResult result = runLaunchService.startReview(AiRunTriggerType.SCHEDULED, "scheduler");
        log.info("AI review prepare result: status={}, runId={}, message={}",
                result.status(), result.runId(), result.message());
    }

    @Job(name = "AI Generation Preparation")
    public void prepareGenerationRunAction() {
        if (!aiProperties.getGeneration().isEnabled() || !aiProperties.getGeneration().isSchedulerEnabled()) {
            return;
        }
        AiRunPreparationResult result = runLaunchService.startGeneration(AiRunTriggerType.SCHEDULED, "scheduler");
        log.info("AI generation prepare result: status={}, runId={}, message={}",
                result.status(), result.runId(), result.message());
    }
}
