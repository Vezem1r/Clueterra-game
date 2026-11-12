package io.clueterra.questions.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public class HintGenerationLogBuilder {
    Long id;
    Long countryId;
    Long batchId;
    String countryCode;
    HintGenerationTriggerType triggerType = HintGenerationTriggerType.MANUAL;
    int hintsAttempted;
    int hintsCreated;
    int hintsDuplicates;
    int hintsFailed;
    BigDecimal avgDifficulty;
    BigDecimal avgQualityScore;
    int categoriesCovered;
    int hintTypesCovered;
    Instant startedAt;
    Instant completedAt;
    Long durationMs;
    String errorMessage;
    Map<String, Object> details;
    Map<String, Object> lensBreakdown;

    public HintGenerationLogBuilder id(Long id) {
        this.id = id;
        return this;
    }

    public HintGenerationLogBuilder countryId(Long countryId) {
        this.countryId = countryId;
        return this;
    }

    public HintGenerationLogBuilder batchId(Long batchId) {
        this.batchId = batchId;
        return this;
    }

    public HintGenerationLogBuilder countryCode(String countryCode) {
        this.countryCode = countryCode;
        return this;
    }

    public HintGenerationLogBuilder triggerType(HintGenerationTriggerType triggerType) {
        this.triggerType = triggerType;
        return this;
    }

    public HintGenerationLogBuilder hintsAttempted(int hintsAttempted) {
        this.hintsAttempted = hintsAttempted;
        return this;
    }

    public HintGenerationLogBuilder hintsCreated(int hintsCreated) {
        this.hintsCreated = hintsCreated;
        return this;
    }

    public HintGenerationLogBuilder hintsDuplicates(int hintsDuplicates) {
        this.hintsDuplicates = hintsDuplicates;
        return this;
    }

    public HintGenerationLogBuilder hintsFailed(int hintsFailed) {
        this.hintsFailed = hintsFailed;
        return this;
    }

    public HintGenerationLogBuilder avgDifficulty(BigDecimal avgDifficulty) {
        this.avgDifficulty = avgDifficulty;
        return this;
    }

    public HintGenerationLogBuilder avgQualityScore(BigDecimal avgQualityScore) {
        this.avgQualityScore = avgQualityScore;
        return this;
    }

    public HintGenerationLogBuilder categoriesCovered(int categoriesCovered) {
        this.categoriesCovered = categoriesCovered;
        return this;
    }

    public HintGenerationLogBuilder hintTypesCovered(int hintTypesCovered) {
        this.hintTypesCovered = hintTypesCovered;
        return this;
    }

    public HintGenerationLogBuilder startedAt(Instant startedAt) {
        this.startedAt = startedAt;
        return this;
    }

    public HintGenerationLogBuilder completedAt(Instant completedAt) {
        this.completedAt = completedAt;
        return this;
    }

    public HintGenerationLogBuilder durationMs(Long durationMs) {
        this.durationMs = durationMs;
        return this;
    }

    public HintGenerationLogBuilder errorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    public HintGenerationLogBuilder details(Map<String, Object> details) {
        this.details = details;
        return this;
    }

    public HintGenerationLogBuilder lensBreakdown(Map<String, Object> lensBreakdown) {
        this.lensBreakdown = lensBreakdown;
        return this;
    }

    public HintGenerationLog build() {
        return new HintGenerationLog(
                id, countryId, batchId, countryCode, triggerType, hintsAttempted, hintsCreated,
                hintsDuplicates, hintsFailed, avgDifficulty, avgQualityScore,
                categoriesCovered, hintTypesCovered, startedAt, completedAt,
                durationMs, errorMessage, details, lensBreakdown
        );
    }
}
