package io.clueterra.questions.ai.config.embedding;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiEmbeddingProperties {

    private boolean enabled = false;
    private String provider = "local-tei";
    private String baseUrl = "http://localhost:18080";
    private String model = "BAAI/bge-small-en-v1.5@5c38ec7";
    private int outputDimensionality = 384;
    private int batchSize = 32;
    private int timeoutSeconds = 30;
    private int healthTimeoutSeconds = 3;
    private String cron = "0 */5 * * * *";
    private int backfillContentsPerRun = 10000;
    private int backfillCountriesPerRun = 300;
    private double candidateThreshold = 0.65;
    private double duplicateThreshold = 0.88;
    private double relatedThreshold = 0.72;
}
