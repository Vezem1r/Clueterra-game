package io.clueterra.questions.ai.config.generation;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiGenerationTokenizationProperties {

    private int maxInputTokensPerRequest = 250_000;
    private double targetInputUtilization = 0.75;
    private int baseRequestOverheadTokens = 10_000;
}
