package io.clueterra.questions.ai.assistant;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.clueterra.questions.ai.config.AiProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class AiPromptLoader {

    private final AiProperties aiProperties;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public AiPromptLoader(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    public String load(String promptFile) {
        return load(aiProperties.getPromptVersion(), promptFile);
    }

    public String load(String promptVersion, String promptFile) {
        String path = "ai/prompts/" + promptVersion + "/" + promptFile;
        return cache.computeIfAbsent(path, this::readPrompt);
    }

    private String readPrompt(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IllegalStateException("AI prompt resource not found: " + path);
        }

        try {
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load AI prompt resource: " + path, e);
        }
    }
}
