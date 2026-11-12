package io.clueterra.questions.ai.assistant;

import java.time.Duration;
import java.util.Locale;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.googleai.GeminiMode;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import io.clueterra.questions.ai.config.AiModelProfile;
import io.clueterra.questions.ai.config.AiProperties;
import org.springframework.stereotype.Component;

@Component
public class GeminiAiChatModelFactory implements AiChatModelFactory {

    private final AiProperties aiProperties;

    private static final String GEMINI_PROVIDER = "gemini";

    public GeminiAiChatModelFactory(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @Override
    public boolean supports(String provider) {
        return GEMINI_PROVIDER.equalsIgnoreCase(provider);
    }

    @Override
    public ChatModel createChatModel(AiModelProfile modelProfile) {
        if (aiProperties.getApiKey() == null || aiProperties.getApiKey().isBlank()) {
            throw new IllegalStateException("AI provider api key is not configured");
        }

        GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder builder = GoogleAiGeminiChatModel.builder()
                .apiKey(aiProperties.getApiKey())
                .modelName(modelProfile.model())
                .temperature(modelProfile.temperature())
                .maxOutputTokens(modelProfile.maxOutputTokens())
                .allowGoogleSearch(modelProfile.allowGoogleSearch())
                .timeout(Duration.ofSeconds(modelProfile.timeoutSeconds()))
                .maxRetries(modelProfile.maxRetries());

        if (modelProfile.thinkingLevel() != null && !modelProfile.thinkingLevel().isBlank()) {
            builder.thinkingConfig(GeminiThinkingConfig.builder()
                    .thinkingLevel(resolveThinkingLevel(modelProfile.thinkingLevel()))
                    .build());
        } else if (modelProfile.thinkingBudget() != null) {
            builder.thinkingConfig(GeminiThinkingConfig.builder()
                    .thinkingBudget(modelProfile.thinkingBudget())
                    .build());
        }

        boolean toolsConfigured = modelProfile.functionCallingMode() != null
                && !modelProfile.functionCallingMode().isBlank();

        if (!toolsConfigured) {
            builder.responseFormat(ResponseFormat.JSON);
        }

        if (toolsConfigured) {
            GeminiMode mode = resolveGeminiMode(modelProfile.functionCallingMode());

            if (mode == GeminiMode.ANY) {
                builder.toolConfig(mode, "searchWikimediaImage", "searchPublicDomainCurrencyImage");
            } else {
                builder.toolConfig(mode);
            }

            builder.returnThinking(Boolean.TRUE);
            builder.sendThinking(Boolean.TRUE);
        }

        return builder.build();
    }

    private GeminiThinkingConfig.GeminiThinkingLevel resolveThinkingLevel(String rawLevel) {
        String normalized = rawLevel.trim().toUpperCase(Locale.ROOT);
        try {
            return GeminiThinkingConfig.GeminiThinkingLevel.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported Gemini thinking level: " + rawLevel, ex);
        }
    }

    private GeminiMode resolveGeminiMode(String rawMode) {
        String normalized = rawMode.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ANY", "REQUIRED" -> GeminiMode.ANY;
            case "NONE" -> GeminiMode.NONE;
            case "AUTO", "" -> GeminiMode.AUTO;
            case "VALIDATED" -> throw new IllegalArgumentException(
                    "VALIDATED is not supported by LangChain4j GeminiMode in this version"
            );
            default -> throw new IllegalArgumentException("Unsupported Gemini function calling mode: " + rawMode);
        };
    }
}
