package io.clueterra.api.game.scheduler;

import java.time.Clock;
import java.time.LocalDate;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import io.clueterra.api.game.repository.GameSessionJdbcRepository;
import io.clueterra.api.budget.repository.BudgetGameSessionJdbcRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ExpiredSessionScheduler {

    private final JobScheduler jobScheduler;
    private final GameSessionJdbcRepository sessionRepository;
    private final BudgetGameSessionJdbcRepository budgetSessionRepository;
    private final Clock clock;

    @Value("${clueterra.sessions.abandon-cron:5 0 * * *}")
    private String abandonSessionsCron;

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleJobs() {
        log.info("[SESSION_CLEANUP] Scheduling expired session cleanup: {}", abandonSessionsCron);
        jobScheduler.scheduleRecurrently("expired-session-cleanup", abandonSessionsCron, this::abandonExpiredSessions);
    }

    @Job(name = "Abandon expired sessions")
    public void abandonExpiredSessions() {
        LocalDate today = LocalDate.now(clock);
        int updated = sessionRepository.abandonExpiredSessions(today);
        int budgetUpdated = budgetSessionRepository.abandonExpiredSessions();
        log.info("[SESSION_CLEANUP] Marked {} game and {} BudgetGuessr sessions as abandoned before {}", updated, budgetUpdated, today);
    }
}
