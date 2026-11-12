package io.clueterra.questions.ai.assistant;

import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.tooling.AiWikimediaImageToolService;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;

@Configuration
@ConditionalOnProperty(prefix = "clueterra.ai", name = "enabled", havingValue = "true")
public class AiAssistantConfig {

    private static final String REVIEW_PROMPT_FILE = "review-system.txt";
    private static final String GENERATION_PROMPT_FILE = "generation-system.txt";
    private static final String GENERATION_IMAGE_VALIDATION_PROMPT_FILE = "generation-image-validation-system.txt";

    @Bean
    public ChatModel aiReviewChatModel(List<AiChatModelFactory> factories, AiProperties aiProperties) {
        return factories.stream()
                .filter(factory -> factory.supports(aiProperties.reviewModelProfile().provider()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Unsupported AI provider: " + aiProperties.reviewModelProfile().provider()))
                .createChatModel(aiProperties.reviewModelProfile());
    }

    @Bean
    public ChatModel aiGenerationChatModel(List<AiChatModelFactory> factories, AiProperties aiProperties) {
        return factories.stream()
                .filter(factory -> factory.supports(aiProperties.generationModelProfile().provider()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Unsupported AI provider: " + aiProperties.generationModelProfile().provider()))
                .createChatModel(aiProperties.generationModelProfile());
    }

    @Bean
    public AiHintReviewAssistant aiHintReviewAssistant(
            @Qualifier("aiReviewChatModel") ChatModel aiReviewChatModel,
            AiPromptLoader promptLoader,
            AiProperties aiProperties) {
        return AiServices.builder(AiHintReviewAssistant.class)
                .chatModel(aiReviewChatModel)
                .systemMessage(promptLoader.load(aiProperties.getReview().getPromptVersion(), REVIEW_PROMPT_FILE))
                .build();
    }

    @Bean
    public AiHintGenerationAssistant aiHintGenerationAssistant(
            @Qualifier("aiGenerationChatModel") ChatModel aiGenerationChatModel,
            AiPromptLoader promptLoader,
            AiWikimediaImageToolService wikimediaImageToolService,
            AiProperties aiProperties) {
        return AiServices.builder(AiHintGenerationAssistant.class)
                .chatModel(aiGenerationChatModel)
                .systemMessage(promptLoader.load(GENERATION_PROMPT_FILE))
                .tools(wikimediaImageToolService)
                .hallucinatedToolNameStrategy(request -> ToolExecutionResultMessage.from(
                        request,
                        "Tool '%s' does not exist. Available tools: searchWikimediaImage, searchPublicDomainCurrencyImage"
                                .formatted(request.name())))
                .maxSequentialToolsInvocations(aiProperties.getMaxToolRoundtripsPerRequest())
                .build();
    }

    @Bean
    public AiGenerationImageValidationAssistant aiGenerationImageValidationAssistant(
            @Qualifier("aiGenerationChatModel") ChatModel aiGenerationChatModel,
            AiPromptLoader promptLoader) {
        return AiServices.builder(AiGenerationImageValidationAssistant.class)
                .chatModel(aiGenerationChatModel)
                .systemMessage(promptLoader.load(GENERATION_IMAGE_VALIDATION_PROMPT_FILE))
                .build();
    }
}
