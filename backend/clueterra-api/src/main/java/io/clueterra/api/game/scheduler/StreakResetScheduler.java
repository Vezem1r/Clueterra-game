package io.clueterra.api.game.scheduler;

import java.util.Map;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class StreakResetScheduler {

    private final JobScheduler jobScheduler;
    private final NamedParameterJdbcTemplate jdbc;

    private static final String CALL_RESET_EXPIRED_STREAKS = """
            SELECT * FROM cl_api_schema.reset_expired_streaks()
            """;

    @Value("${clueterra.streak.reset-cron:1 0 * * *}")
    private String streakResetCron;

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleJobs() {
        log.info("[STREAK_SCHEDULER] Scheduling nightly streak reset: {}", streakResetCron);
        jobScheduler.scheduleRecurrently("streak-reset", streakResetCron, this::resetExpiredStreaks);
    }

    @Job(name = "Nightly streak reset")
    public void resetExpiredStreaks() {
        log.info("[STREAK_SCHEDULER] Resetting expired streaks");
        var result = jdbc.queryForMap(CALL_RESET_EXPIRED_STREAKS, Map.of());
        int freezesConsumed = ((Number) result.get("freezes_consumed")).intValue();
        int streaksReset = ((Number) result.get("streaks_reset")).intValue();
        log.info("[STREAK_SCHEDULER] Consumed {} freezes, reset {} streaks", freezesConsumed, streaksReset);
    }
}
