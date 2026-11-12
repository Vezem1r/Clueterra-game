package io.clueterra.questions.ai.config.generation;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiGenerationQuotaProperties {

    private int requestsPerMinute = 15;
    private long inputTokensPerMinute = 250_000L;
    private int requestsPerDay = 500;
    private String dayResetTimeZone = "America/Los_Angeles";
}
