package io.clueterra.questions.ai.config.review;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Getter
@Setter
public class AiReviewProperties {

    private boolean enabled = true;
    private boolean schedulerEnabled = false;
    private String cron = "0 15 * * * *";
    private String promptVersion = "review-v2";
    private String model = "gemini-3.1-flash-lite";
    private double temperature = 1.0;
    private int timeoutSeconds = 180;
    private int maxRetries = 0;
    private int maxOutputTokensPerRequest = 28_000;
    private Integer thinkingBudget;
    private String thinkingLevel;
    private boolean allowGoogleSearch = false;
    private int maxRequestsPerRun = 5;
    private int defaultBatchSizeSoftCap = 8;
    private boolean countFailedRequestsAfterDispatch = true;
    private boolean countGroundedPromptsPerRequest = true;
    @NestedConfigurationProperty
    private AiReviewQuotaProperties quota = new AiReviewQuotaProperties();
    @NestedConfigurationProperty
    private AiReviewTokenizationProperties tokenization = new AiReviewTokenizationProperties();
}
