package io.clueterra.api.auth.scheduler;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Component;

import io.clueterra.api.auth.repository.AuthJdbcRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class GuestCleanupScheduler {

    private static final String CRON_DAILY_3AM = "0 3 * * *";

    private final JobScheduler jobScheduler;
    private final AuthJdbcRepository authRepository;

    @PostConstruct
    public void schedule() {
        jobScheduler.scheduleRecurrently("guest-cleanup", CRON_DAILY_3AM, this::cleanupExpiredGuests);
    }

    @Job(name = "Cleanup expired guest users")
    public void cleanupExpiredGuests() {
        int deleted = authRepository.deleteExpiredGuests();
        if (deleted > 0) {
            log.info("Deleted {} expired guest users", deleted);
        }
    }
}
