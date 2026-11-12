package io.clueterra.budget.generation.challenge.scheduler;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import io.clueterra.budget.config.BudgetGenerationProperties;
import io.clueterra.budget.generation.challenge.model.BudgetChallengeGenerationSummary;
import io.clueterra.budget.generation.challenge.service.BudgetDailyChallengeGenerationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "clueterra.budget.generation",
        name = "scheduler-enabled",
        havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class BudgetChallengeScheduledJobs {

    private static final String ROLLING_WINDOW_JOB_ID = "budget-daily-challenges-next-week";

    private final JobScheduler jobScheduler;
    private final BudgetGenerationProperties properties;
    private final BudgetDailyChallengeGenerationService generationService;

    @EventListener(ApplicationReadyEvent.class)
    public void schedule() {
        jobScheduler.scheduleRecurrently(
                ROLLING_WINDOW_JOB_ID,
                properties.dailyCron(),
                this::maintainRollingWindow);
        log.info("Budget daily challenge rolling-window scheduler registered: cron={}", properties.dailyCron());
    }

    @Job(name = "BudgetGuessr rolling daily challenge maintenance", retries = 2)
    public void maintainRollingWindow() {
        BudgetChallengeGenerationSummary summary = generationService.generateRollingWindow();
        log.info(
                "BudgetGuessr rolling-window maintenance finished: {} created, {} occupied",
                summary.createdChallenges(),
                summary.skippedChallenges());
    }
}
