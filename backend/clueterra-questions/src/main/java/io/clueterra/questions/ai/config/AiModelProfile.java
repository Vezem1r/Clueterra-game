package io.clueterra.questions.ai.config;

public record AiModelProfile(
        String provider,
        String model,
        double temperature,
        int maxOutputTokens,
        boolean allowGoogleSearch,
        int timeoutSeconds,
        int maxRetries,
        String functionCallingMode,
        Integer thinkingBudget,
        String thinkingLevel) {
}
