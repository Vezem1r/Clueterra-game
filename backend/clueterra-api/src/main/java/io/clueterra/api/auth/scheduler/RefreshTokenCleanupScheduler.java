package io.clueterra.api.auth.scheduler;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Component;

import io.clueterra.api.auth.service.RefreshTokenService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupScheduler {

    private static final String CRON_DAILY_330AM = "30 3 * * *";

    private final JobScheduler jobScheduler;
    private final RefreshTokenService refreshTokenService;

    @PostConstruct
    public void schedule() {
        jobScheduler.scheduleRecurrently("refresh-token-cleanup", CRON_DAILY_330AM, this::cleanupExpiredRefreshTokens);
    }

    @Job(name = "Cleanup expired refresh tokens")
    public void cleanupExpiredRefreshTokens() {
        int deleted = refreshTokenService.deleteExpired();
        if (deleted > 0) {
            log.info("Deleted {} expired refresh tokens", deleted);
        }
    }
}
