package io.clueterra.questions.ai.config.review;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiReviewTokenizationProperties {

    private int maxInputTokensPerRequest = 250_000;
    private double targetInputUtilization = 0.75;
    private boolean allowImages = true;
    private int estimatedImageTokens = 1_200;
    private int baseRequestOverheadTokens = 6_000;
}
