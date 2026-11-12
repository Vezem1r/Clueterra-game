package io.clueterra.questions.ai.assistant;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;

import java.util.List;

public interface AiGenerationImageValidationAssistant {

    Result<String> validate(@UserMessage List<Content> contents);
}
