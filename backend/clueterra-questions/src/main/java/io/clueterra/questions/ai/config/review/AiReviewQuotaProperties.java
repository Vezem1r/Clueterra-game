package io.clueterra.questions.ai.config.review;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiReviewQuotaProperties {

    private int requestsPerMinute = 15;
    private long inputTokensPerMinute = 250_000L;
    private int requestsPerDay = 500;
    private Integer groundedPromptsPerDay = 1_500;
    private String dayResetTimeZone = "America/Los_Angeles";
}
