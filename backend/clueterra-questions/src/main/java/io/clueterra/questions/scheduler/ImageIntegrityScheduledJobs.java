package io.clueterra.questions.scheduler;

import io.clueterra.questions.image.ContentRetirementService;
import io.clueterra.questions.image.ImageIntegrityService;
import io.clueterra.questions.image.MediaGarbageCollectionResult;
import io.clueterra.questions.image.MediaGarbageCollectionService;
import io.clueterra.questions.storage.ImageStorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ImageIntegrityScheduledJobs {

    static final String MEDIA_MAINTENANCE_JOB_ID = "canonical-media-maintenance";

    private final JobScheduler jobScheduler;
    private final ImageIntegrityService imageIntegrityService;
    private final ContentRetirementService contentRetirementService;
    private final MediaGarbageCollectionService garbageCollectionService;
    private final ImageStorageProperties storageProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleJobs() {
        if (!storageProperties.isMaintenanceSchedulerEnabled()) {
            log.info("[MEDIA_MAINTENANCE] Canonical media maintenance scheduler is disabled");
            jobScheduler.deleteRecurringJob(MEDIA_MAINTENANCE_JOB_ID);
            return;
        }
        String maintenanceCron = storageProperties.getMaintenanceCron();
        log.info("[MEDIA_MAINTENANCE] Scheduling canonical media maintenance: {}", maintenanceCron);
        jobScheduler.scheduleRecurrently(
                MEDIA_MAINTENANCE_JOB_ID,
                maintenanceCron,
                this::runMediaMaintenance);
    }

    @Job(name = "Canonical Media Audit, Retirement and Garbage Collection", retries = 1)
    public void runMediaMaintenance() {
        contentRetirementService.retireSuppressedContent();
        imageIntegrityService.runScheduledAudit();
        MediaGarbageCollectionResult garbageCollection = garbageCollectionService.collect();
        if (garbageCollection.failures() > 0) {
            throw new IllegalStateException(
                    "Canonical media garbage collection completed with "
                            + garbageCollection.failures()
                            + " failures");
        }
    }
}
