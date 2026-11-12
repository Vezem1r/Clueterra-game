package io.clueterra.questions.ai.assistant;

import java.util.List;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;

public interface AiHintGenerationAssistant {

    Result<String> generate(@UserMessage List<Content> contents);
}
