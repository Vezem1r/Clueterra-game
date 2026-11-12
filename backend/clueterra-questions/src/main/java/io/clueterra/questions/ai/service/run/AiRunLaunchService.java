package io.clueterra.questions.ai.service.run;

import io.clueterra.questions.ai.model.run.AiRunPreparationResult;
import io.clueterra.questions.ai.model.run.AiRunStatus;
import io.clueterra.questions.ai.model.run.AiRunTriggerType;
import io.clueterra.questions.ai.service.generation.AiGenerationPreparationService;
import io.clueterra.questions.ai.service.review.AiReviewPreparationService;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnProperty(prefix = "clueterra.ai", name = "enabled", havingValue = "true")
public class AiRunLaunchService {

    private final JobScheduler jobScheduler;
    private final AiReviewPreparationService reviewPreparationService;
    private final AiGenerationPreparationService generationPreparationService;
    private final AiRunExecutionService runExecutionService;

    public AiRunLaunchService(
            JobScheduler jobScheduler,
            AiReviewPreparationService reviewPreparationService,
            AiGenerationPreparationService generationPreparationService,
            AiRunExecutionService runExecutionService) {
        this.jobScheduler = jobScheduler;
        this.reviewPreparationService = reviewPreparationService;
        this.generationPreparationService = generationPreparationService;
        this.runExecutionService = runExecutionService;
    }

    public AiRunPreparationResult startReview(AiRunTriggerType triggerType, String requestedBy) {
        log.info("AI review launch requested: triggerType={}, requestedBy={}", triggerType, requestedBy);
        AiRunPreparationResult result = reviewPreparationService.prepare(triggerType, requestedBy);
        enqueueIfPrepared(result);
        return result;
    }

    public AiRunPreparationResult startGeneration(AiRunTriggerType triggerType, String requestedBy) {
        log.info("AI generation launch requested: triggerType={}, requestedBy={}", triggerType, requestedBy);
        AiRunPreparationResult result = generationPreparationService.prepare(triggerType, requestedBy);
        enqueueIfPrepared(result);
        return result;
    }

    private void enqueueIfPrepared(AiRunPreparationResult result) {
        if (result.runId() != null && result.status() == AiRunStatus.PREPARED) {
            log.info(
                    "AI run prepared and enqueued: runId={}, runType={}, stages={}, items={}, estimatedInputTokens={}",
                    result.runId(),
                    result.runType(),
                    result.preparedStages(),
                    result.preparedItems(),
                    result.estimatedInputTokens()
            );
            jobScheduler.enqueue(() -> runExecutionService.executeRun(result.runId()));
            return;
        }
        log.info(
                "AI run not enqueued: runId={}, runType={}, status={}, message={}",
                result.runId(),
                result.runType(),
                result.status(),
                result.message()
        );
    }
}
