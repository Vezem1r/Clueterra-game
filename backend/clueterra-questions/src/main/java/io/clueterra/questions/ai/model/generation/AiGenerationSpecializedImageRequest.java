package io.clueterra.questions.ai.model.generation;

public record AiGenerationSpecializedImageRequest(
        int landmarkCount,
        int currencyCount) {

    public static AiGenerationSpecializedImageRequest none() {
        return new AiGenerationSpecializedImageRequest(0, 0);
    }
}
