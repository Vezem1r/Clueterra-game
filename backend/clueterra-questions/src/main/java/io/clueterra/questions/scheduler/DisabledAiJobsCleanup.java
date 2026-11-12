package io.clueterra.questions.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "clueterra.ai", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledAiJobsCleanup {

    private final JobScheduler jobScheduler;

    @EventListener(ApplicationReadyEvent.class)
    public void removeAiRecurringJobs() {
        jobScheduler.deleteRecurringJob(AiScheduledJobs.REVIEW_JOB_ID);
        jobScheduler.deleteRecurringJob(AiScheduledJobs.GENERATION_JOB_ID);
        jobScheduler.deleteRecurringJob(AiScheduledJobs.EMBEDDING_JOB_ID);
        log.info("AI is disabled; AI recurring jobs removed");
    }
}
