package io.clueterra.questions.controller.dto;

import java.time.Clock;
import java.time.Instant;

public record HealthResponse(
        String status,
        String service,
        Instant timestamp
) {
    public static HealthResponse up(Clock clock) {
        return new HealthResponse("UP", "clueterra-questions", Instant.now(clock));
    }
}
