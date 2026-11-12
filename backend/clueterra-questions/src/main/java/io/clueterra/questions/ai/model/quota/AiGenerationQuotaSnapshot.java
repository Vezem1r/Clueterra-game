package io.clueterra.questions.ai.model.quota;

import java.time.Instant;

public record AiGenerationQuotaSnapshot(
        String provider,
        String model,
        boolean googleSearchEnabled,
        boolean countFailedRequestsAfterDispatch,
        String dayResetTimeZone,
        Instant sampledAt,
        Instant currentMinuteWindowStartedAt,
        Instant currentDayWindowStartedAt,
        Instant nextDayResetAt,
        int requestsPerMinuteLimit,
        int requestsLastMinuteUsed,
        int requestsLastMinuteRemaining,
        long inputTokensPerMinuteLimit,
        long inputTokensLastMinuteUsed,
        long inputTokensLastMinuteRemaining,
        int requestsPerDayLimit,
        int requestsTodayUsed,
        int requestsTodayRemaining,
        long inputTokensTodayUsed,
        long outputTokensTodayUsed,
        int failedRequestsToday) {
}
