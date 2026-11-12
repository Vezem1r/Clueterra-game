package io.clueterra.questions.scheduler;

import io.clueterra.questions.clients.brands.BrandClient;
import io.clueterra.questions.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Slf4j
@Configuration
public class DynamicClientScheduledJobs {

    static final String BRANDS_JOB_ID = "brands-annual-sync";

    private final JobScheduler jobScheduler;
    private final DynamicSyncExecutor syncExecutor;
    private final BrandClient brandClient;
    private final String brandsCron;

    public DynamicClientScheduledJobs(
            JobScheduler jobScheduler,
            DynamicSyncExecutor syncExecutor,
            BrandClient brandClient,
            AppProperties appProperties) {
        this.jobScheduler = jobScheduler;
        this.syncExecutor = syncExecutor;
        this.brandClient = brandClient;
        this.brandsCron = appProperties.getDynamic().getGlobalBrands().getCron();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleJobs() {
        if (!brandClient.isAvailable()) {
            jobScheduler.deleteRecurringJob(BRANDS_JOB_ID);
            log.info("[BRANDS] Client is disabled, recurring sync removed");
            return;
        }
        log.info("[BRANDS] Scheduling sync: {}", brandsCron);
        jobScheduler.scheduleRecurrently(BRANDS_JOB_ID, brandsCron, this::syncBrands);
    }

    @Job(name = "Global Brands Annual Sync", retries = 1)
    public void syncBrands() {
        syncExecutor.execute(brandClient);
    }
}
