package io.clueterra.questions.scheduler;

import io.clueterra.questions.config.AppProperties;
import io.clueterra.questions.config.ChallengeProperties;
import io.clueterra.questions.entity.DailyChallenge;
import io.clueterra.questions.entity.OperationBatchLog;
import io.clueterra.questions.entity.OperationType;
import io.clueterra.questions.entity.OperationTriggerType;
import io.clueterra.questions.repository.OperationBatchLogRepository;
import io.clueterra.questions.service.challenge.DailyChallengeGeneratorService;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Configuration
public class ChallengeScheduledJobs {

    private final JobScheduler jobScheduler;
    private final DailyChallengeGeneratorService challengeGenerator;
    private final OperationBatchLogRepository batchLogRepository;
    private final ChallengeProperties properties;

    public ChallengeScheduledJobs(
            JobScheduler jobScheduler,
            DailyChallengeGeneratorService challengeGenerator,
            OperationBatchLogRepository batchLogRepository,
            AppProperties appProperties) {
        this.jobScheduler = jobScheduler;
        this.challengeGenerator = challengeGenerator;
        this.batchLogRepository = batchLogRepository;
        this.properties = appProperties.getChallenge();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleJobs() {
        jobScheduler.scheduleRecurrently("daily-challenge", properties.getCron().getDailyGeneration(),
                this::generateDailyChallengeAction);
        jobScheduler.scheduleRecurrently("weekly-pregen", properties.getCron().getWeeklyPreGeneration(),
                this::generateWeeklyChallengesAction);
        jobScheduler.scheduleRecurrently("cleanup-old", properties.getCron().getOldChallengesCleanup(),
                this::deactivateOldChallengesAction);
    }

    @Job(name = "Daily Challenge Generation", retries = 3)
    public void generateDailyChallengeAction() {
        runRecorded("daily challenge generation", batchLog -> {
            List<DailyChallenge> challenges = challengeGenerator.generateTodaysChallenges();
            Map<String, Object> summary = new LinkedHashMap<>();
            for (DailyChallenge challenge : challenges) {
                summary.put(challenge.getChallengeLevel().name(), Map.of(
                        "difficulty", challenge.getDifficulty().name(),
                        "countries", challenge.getTotalCountries(),
                        "hints", challenge.getTotalHints()));
            }
            batchLog.setTotalItemsCount(challenges.size());
            batchLog.addSuccesses(challenges.size());
            return summary;
        });
    }

    @Job(name = "Weekly Pre-Generation", retries = 3)
    public void generateWeeklyChallengesAction() {
        runRecorded("weekly challenges pre-generation", batchLog -> {
            List<DailyChallenge> challenges = challengeGenerator.generateUpcomingChallenges(properties.getUpcomingDaysToGenerate());
            batchLog.setTotalItemsCount(challenges.size());
            batchLog.addSuccesses(challenges.size());
            return Map.of(
                    "daysPreGenerated", properties.getUpcomingDaysToGenerate(),
                    "totalChallenges", challenges.size());
        });
    }

    @Job(name = "Deactivate Old Challenges", retries = 2)
    public void deactivateOldChallengesAction() {
        runRecorded("old challenges deactivation", batchLog -> {
            int deactivated = challengeGenerator.deactivateOldChallenges(properties.getOldChallengesRetentionDays());
            batchLog.setTotalItemsCount(deactivated);
            batchLog.addSuccesses(deactivated);
            return Map.of(
                    "retentionDays", properties.getOldChallengesRetentionDays(),
                    "deactivatedCount", deactivated,
                    "operationType", "CLEANUP");
        });
    }

    private void runRecorded(String description, Function<OperationBatchLog, Map<String, Object>> body) {
        log.info("Starting {}", description);
        OperationBatchLog batchLog = OperationBatchLog.start(OperationType.CHALLENGE_GENERATION, OperationTriggerType.SCHEDULED);
        batchLogRepository.save(batchLog);
        try {
            Map<String, Object> summary = body.apply(batchLog);
            batchLog.complete();
            batchLog.setSummary(summary);
            log.info("Finished {}: {}", description, summary);
        } catch (RuntimeException e) {
            log.error("{} failed", description, e);
            batchLog.fail(e);
            throw e;
        } finally {
            batchLogRepository.update(batchLog);
        }
    }
}
