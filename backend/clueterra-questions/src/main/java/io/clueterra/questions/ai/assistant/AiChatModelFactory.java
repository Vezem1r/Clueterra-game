package io.clueterra.questions.ai.assistant;

import dev.langchain4j.model.chat.ChatModel;
import io.clueterra.questions.ai.config.AiModelProfile;

public interface AiChatModelFactory {

    boolean supports(String provider);

    ChatModel createChatModel(AiModelProfile modelProfile);
}
