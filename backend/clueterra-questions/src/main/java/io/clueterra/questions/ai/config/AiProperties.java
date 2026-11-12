package io.clueterra.questions.ai.config;

import io.clueterra.questions.ai.config.embedding.AiEmbeddingProperties;
import io.clueterra.questions.ai.config.generation.AiGenerationProperties;
import io.clueterra.questions.ai.config.review.AiReviewProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Getter
@Setter
@ConfigurationProperties(prefix = "clueterra.ai")
public class AiProperties {

    private boolean enabled = false;
    private String provider = "gemini";
    private String apiKey;
    private String promptVersion = "v2";
    private int maxToolRoundtripsPerRequest = 12;
    @NestedConfigurationProperty
    private AiProviderRetryProperties providerRetry = new AiProviderRetryProperties();
    @NestedConfigurationProperty
    private AiReviewProperties review = new AiReviewProperties();
    @NestedConfigurationProperty
    private AiGenerationProperties generation = new AiGenerationProperties();
    @NestedConfigurationProperty
    private AiRunRecoveryProperties recovery = new AiRunRecoveryProperties();
    @NestedConfigurationProperty
    private AiEmbeddingProperties embedding = new AiEmbeddingProperties();

    public AiModelProfile reviewModelProfile() {
        return new AiModelProfile(
                provider,
                review.getModel(),
                review.getTemperature(),
                review.getMaxOutputTokensPerRequest(),
                review.isAllowGoogleSearch(),
                review.getTimeoutSeconds(),
                review.getMaxRetries(),
                null,
                review.getThinkingBudget(),
                review.getThinkingLevel()
        );
    }

    public AiModelProfile generationModelProfile() {
        return new AiModelProfile(
                provider,
                generation.getModel(),
                generation.getTemperature(),
                generation.getMaxOutputTokensPerRequest(),
                generation.isAllowGoogleSearch(),
                generation.getTimeoutSeconds(),
                generation.getMaxRetries(),
                generation.getFunctionCallingMode(),
                generation.getThinkingBudget(),
                generation.getThinkingLevel()
        );
    }
}
