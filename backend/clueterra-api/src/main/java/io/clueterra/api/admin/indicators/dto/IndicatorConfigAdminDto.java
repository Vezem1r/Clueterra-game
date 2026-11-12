package io.clueterra.api.admin.indicators.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IndicatorConfigAdminDto(
        String indicatorCode,
        String displayName,
        String category,
        String unit,
        Double weight,
        String profileLevel,
        Boolean lowerIsBetter,
        Boolean excludeFromRanking,
        Boolean excludeFromPercentile,
        String hintTemplate,
        String hintType,
        Integer baseDifficulty,
        Double qualityScore,
        Boolean isActive,
        Boolean highInterest,
        Boolean isTrending,
        String trendingFrequency,
        int hintCount,
        String exampleHint,
        String indicatorKind,
        String familyType,
        Integer maxAmbiguityCount,
        Integer componentCount,
        Integer predicateCount,
        Integer variantCount,
        String componentsSummary,
        String predicatesSummary,
        String variantTemplates) {
}
