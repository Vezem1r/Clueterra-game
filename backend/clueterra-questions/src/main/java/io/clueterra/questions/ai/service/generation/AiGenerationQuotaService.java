package io.clueterra.questions.ai.service.generation;

import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.config.generation.AiGenerationProperties;
import io.clueterra.questions.ai.config.generation.AiGenerationQuotaProperties;
import io.clueterra.questions.ai.model.quota.AiGenerationQuotaSnapshot;
import io.clueterra.questions.ai.model.quota.AiModelUsageWindowAggregate;
import io.clueterra.questions.ai.repository.AiExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiGenerationQuotaService {

    private static final long MINUTE_CAPACITY_POLL_MILLIS = 2_000L;

    private final AiExecutionRepository executionRepository;
    private final AiProperties aiProperties;
    private final Clock clock;

    public AiGenerationQuotaSnapshot getSnapshot() {
        Instant now = Instant.now(clock);
        AiGenerationProperties gen = aiProperties.getGeneration();
        AiGenerationQuotaProperties quota = gen.getQuota();
        ZoneId resetZone = ZoneId.of(quota.getDayResetTimeZone());
        Instant minuteWindowStart = now.minus(Duration.ofMinutes(1));
        ZonedDateTime zonedNow = now.atZone(resetZone);
        ZonedDateTime currentDayWindow = zonedNow.toLocalDate().atStartOfDay(resetZone);
        ZonedDateTime nextDayWindow = currentDayWindow.plusDays(1);

        AiModelUsageWindowAggregate aggregate = executionRepository.findUsageSnapshotForModel(
                aiProperties.generationModelProfile().provider(),
                aiProperties.generationModelProfile().model(),
                minuteWindowStart,
                minuteWindowStart.isBefore(currentDayWindow.toInstant()) ? minuteWindowStart : currentDayWindow.toInstant(),
                currentDayWindow.toInstant(),
                nextDayWindow.toInstant()
        );

        return new AiGenerationQuotaSnapshot(
                aiProperties.generationModelProfile().provider(),
                aiProperties.generationModelProfile().model(),
                aiProperties.generationModelProfile().allowGoogleSearch(),
                gen.isCountFailedRequestsAfterDispatch(),
                quota.getDayResetTimeZone(),
                now,
                minuteWindowStart,
                currentDayWindow.toInstant(),
                nextDayWindow.toInstant(),
                quota.getRequestsPerMinute(),
                aggregate.requestsLastMinute(),
                Math.max(0, quota.getRequestsPerMinute() - aggregate.requestsLastMinute()),
                quota.getInputTokensPerMinute(),
                aggregate.inputTokensLastMinute(),
                Math.max(0, quota.getInputTokensPerMinute() - aggregate.inputTokensLastMinute()),
                quota.getRequestsPerDay(),
                aggregate.requestsToday(),
                Math.max(0, quota.getRequestsPerDay() - aggregate.requestsToday()),
                aggregate.inputTokensToday(),
                aggregate.outputTokensToday(),
                aggregate.failedRequestsToday()
        );
    }

    public String summarizeSnapshot(AiGenerationQuotaSnapshot snapshot) {
        return "model=" + snapshot.model()
                + ", rpm=" + snapshot.requestsLastMinuteUsed() + "/" + snapshot.requestsPerMinuteLimit()
                + ", tpm=" + snapshot.inputTokensLastMinuteUsed() + "/" + snapshot.inputTokensPerMinuteLimit()
                + ", rpd=" + snapshot.requestsTodayUsed() + "/" + snapshot.requestsPerDayLimit();
    }

    public void awaitMinuteCapacity(long estimatedInputTokens) {
        AiGenerationQuotaSnapshot snapshot = getSnapshot();
        if (estimatedInputTokens > snapshot.inputTokensPerMinuteLimit()) {
            throw new IllegalArgumentException(
                    "Estimated generation input exceeds the configured TPM limit: estimated="
                            + estimatedInputTokens
                            + ", limit=" + snapshot.inputTokensPerMinuteLimit());
        }

        boolean waitingLogged = false;
        while (snapshot.requestsLastMinuteRemaining() < 1
                || snapshot.inputTokensLastMinuteRemaining() < estimatedInputTokens) {
            if (!waitingLogged) {
                log.info("Waiting for temporary Gemini generation quota before dispatch: {}",
                        summarizeSnapshot(snapshot));
                waitingLogged = true;
            }
            sleepBeforeQuotaRecheck();
            snapshot = getSnapshot();
        }
        if (waitingLogged) {
            log.info("Temporary Gemini generation quota is available again: {}",
                    summarizeSnapshot(snapshot));
        }
    }

    private void sleepBeforeQuotaRecheck() {
        try {
            Thread.sleep(MINUTE_CAPACITY_POLL_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for generation quota", ex);
        }
    }
}
