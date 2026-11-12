package io.clueterra.questions.ai.model.quota;

public record AiModelUsageWindowAggregate(
        int requestsLastMinute,
        long inputTokensLastMinute,
        int requestsToday,
        int groundedPromptsToday,
        int failedRequestsToday,
        long inputTokensToday,
        long outputTokensToday) {
}
