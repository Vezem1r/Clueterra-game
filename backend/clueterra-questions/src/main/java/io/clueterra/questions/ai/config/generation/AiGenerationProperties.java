package io.clueterra.questions.ai.config.generation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Getter
@Setter
public class AiGenerationProperties {

    private boolean enabled = true;
    private boolean schedulerEnabled = false;
    private String cron = "0 30 */4 * * *";
    private String model = "gemini-3.1-flash-lite";
    private double temperature = 1.0;
    private int timeoutSeconds = 180;
    private int maxRetries = 0;
    private int maxOutputTokensPerRequest = 24_000;
    private Integer thinkingBudget;
    private String thinkingLevel = "MEDIUM";
    private boolean allowGoogleSearch = false;
    private String functionCallingMode = "AUTO";
    private int maxRequestsPerRun = 1;
    private int countryBatchSizeSoftCap = 1;
    private int minActiveHintsThreshold = 40;
    private int requestedProposalsPerCountry = 12;
    private int maxProposalsPerCountry = 40;
    private int maxEmbeddingRepairPasses = 3;
    private double targetImageShare = 0.15;
    private int specializedImageSearchAttempts = 4;
    private int targetLandmarkImageHintsPerCountry = 3;
    private int targetCurrencyImageHintsPerCountry = 2;
    private int specializedImageRetryCooldownDays = 30;
    private boolean countFailedRequestsAfterDispatch = true;
    @NestedConfigurationProperty
    private AiGenerationQuotaProperties quota = new AiGenerationQuotaProperties();
    @NestedConfigurationProperty
    private AiGenerationTokenizationProperties tokenization = new AiGenerationTokenizationProperties();
}
