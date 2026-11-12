package io.clueterra.questions.ai.dto.overview;

import java.time.Instant;

public record AiQuotaUsageResponse(
        int requestsPerMinuteUsed,
        int requestsPerMinuteLimit,
        int requestsPerDayUsed,
        int requestsPerDayLimit,
        Integer groundedPromptsPerDayUsed,
        Integer groundedPromptsPerDayLimit,
        long inputTokensToday,
        long outputTokensToday,
        int failedToday,
        Instant nextResetAt) {
}
