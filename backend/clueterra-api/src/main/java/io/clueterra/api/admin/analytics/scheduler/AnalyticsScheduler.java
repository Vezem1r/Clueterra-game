package io.clueterra.api.admin.analytics.scheduler;

import java.time.Clock;
import java.time.LocalDate;

import io.clueterra.api.admin.analytics.service.AnalyticsService;
import io.clueterra.api.game.repository.GameSessionJdbcRepository;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AnalyticsScheduler {

    private final JobScheduler jobScheduler;
    private final AnalyticsService analyticsService;
    private final GameSessionJdbcRepository sessionRepository;
    private final Clock clock;

    @Value("${clueterra.analytics.cron}")
    private String analyticsCron;

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleJobs() {
        log.info("[ANALYTICS_SCHEDULER] Scheduling analytics aggregation: {}", analyticsCron);
        jobScheduler.scheduleRecurrently("analytics-aggregation", analyticsCron, this::aggregateYesterdayAction);
    }

    @Job(name = "Daily Analytics Aggregation")
    public void aggregateYesterdayAction() {
        LocalDate today = LocalDate.now(clock);
        int abandoned = sessionRepository.abandonExpiredSessions(today);
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        log.info("[ANALYTICS_SCHEDULER] Marked {} expired sessions as abandoned before {}", abandoned, today);
        log.info("[ANALYTICS_SCHEDULER] Aggregating analytics for {}", yesterday);
        analyticsService.aggregateDate(yesterday);
        log.info("[ANALYTICS_SCHEDULER] Aggregation completed for {}", yesterday);
    }
}
