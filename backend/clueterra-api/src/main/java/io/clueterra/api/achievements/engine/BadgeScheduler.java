package io.clueterra.api.achievements.engine;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import io.clueterra.api.common.enums.EvaluationType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class BadgeScheduler {

    private final JobScheduler jobScheduler;
    private final BadgeEvaluationService evaluationService;

    @Value("${clueterra.badges.cron:0 2 * * *}")
    private String badgeCron;

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleJobs() {
        log.info("[BADGE_SCHEDULER] Scheduling nightly badge evaluation: {}", badgeCron);
        jobScheduler.scheduleRecurrently("badge-evaluation", badgeCron, this::nightlyEvaluation);
    }

    @Job(name = "Nightly badge evaluation")
    public void nightlyEvaluation() {
        log.info("[BADGE_SCHEDULER] Starting nightly badge evaluation");
        evaluationService.evaluateAllCustomBadges(EvaluationType.NIGHTLY.name());
        log.info("[BADGE_SCHEDULER] Nightly badge evaluation completed");
    }
}
