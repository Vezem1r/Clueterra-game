package io.clueterra.questions.ai.service.review;

import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.config.review.AiReviewProperties;
import io.clueterra.questions.ai.config.review.AiReviewQuotaProperties;
import io.clueterra.questions.ai.model.quota.AiModelUsageWindowAggregate;
import io.clueterra.questions.ai.model.quota.AiReviewQuotaSnapshot;
import io.clueterra.questions.ai.repository.AiExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
public class AiReviewQuotaService {

    private final AiExecutionRepository executionRepository;
    private final AiProperties aiProperties;
    private final Clock clock;

    public AiReviewQuotaSnapshot getSnapshot() {
        Instant now = Instant.now(clock);
        AiReviewProperties rev = aiProperties.getReview();
        AiReviewQuotaProperties quota = rev.getQuota();
        ZoneId resetZone = ZoneId.of(quota.getDayResetTimeZone());
        Instant minuteWindowStart = now.minus(Duration.ofMinutes(1));
        ZonedDateTime zonedNow = now.atZone(resetZone);
        ZonedDateTime currentDayWindow = zonedNow.toLocalDate().atStartOfDay(resetZone);
        ZonedDateTime nextDayWindow = currentDayWindow.plusDays(1);

        AiModelUsageWindowAggregate aggregate = executionRepository.findUsageSnapshotForModel(
                aiProperties.reviewModelProfile().provider(),
                aiProperties.reviewModelProfile().model(),
                minuteWindowStart,
                minuteWindowStart.isBefore(currentDayWindow.toInstant()) ? minuteWindowStart : currentDayWindow.toInstant(),
                currentDayWindow.toInstant(),
                nextDayWindow.toInstant()
        );

        int requestsPerDayRemaining = Math.max(0, quota.getRequestsPerDay() - aggregate.requestsToday());
        Integer groundedPromptsPerDayRemaining = quota.getGroundedPromptsPerDay() == null
                ? null
                : Math.max(0, quota.getGroundedPromptsPerDay() - aggregate.groundedPromptsToday());

        return new AiReviewQuotaSnapshot(
                aiProperties.reviewModelProfile().provider(),
                aiProperties.reviewModelProfile().model(),
                aiProperties.reviewModelProfile().allowGoogleSearch(),
                rev.isCountFailedRequestsAfterDispatch(),
                rev.isCountGroundedPromptsPerRequest(),
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
                requestsPerDayRemaining,
                quota.getGroundedPromptsPerDay(),
                aggregate.groundedPromptsToday(),
                groundedPromptsPerDayRemaining,
                aggregate.inputTokensToday(),
                aggregate.outputTokensToday(),
                aggregate.failedRequestsToday()
        );
    }

    public String summarizeSnapshot(AiReviewQuotaSnapshot snapshot) {
        String groundedSummary = snapshot.groundedPromptsPerDayLimit() == null
                ? "unlimited"
                : snapshot.groundedPromptsTodayUsed() + "/" + snapshot.groundedPromptsPerDayLimit();
        return "model=" + snapshot.model()
                + ", rpm=" + snapshot.requestsLastMinuteUsed() + "/" + snapshot.requestsPerMinuteLimit()
                + ", tpm=" + snapshot.inputTokensLastMinuteUsed() + "/" + snapshot.inputTokensPerMinuteLimit()
                + ", rpd=" + snapshot.requestsTodayUsed() + "/" + snapshot.requestsPerDayLimit()
                + ", grounded=" + groundedSummary;
    }
}
