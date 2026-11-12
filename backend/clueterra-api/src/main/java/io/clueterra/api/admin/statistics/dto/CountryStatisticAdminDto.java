package io.clueterra.api.admin.statistics.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record CountryStatisticAdminDto(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long countryId,
        String countryCode,
        String countryName,
        String source,
        String indicatorCode,
        String indicatorName,
        String category,
        BigDecimal value,
        String valueText,
        String unit,
        Integer year,
        Integer globalRank,
        Integer regionalRank,
        BigDecimal percentile,
        BigDecimal deviationScore,
        Boolean isCurrent,
        Boolean manualOverride,
        BigDecimal confidence,
        BigDecimal weight,
        String fetchedAt,
        String imageUrl,
        String originalImageUrl,
        String imageSourcePageUrl,
        String imageAuthorName,
        String imageAttributionText,
        String imageLicenseLabel,
        String imageLicenseUrl) {

    public CountryStatisticAdminDto withImageUrl(String resolvedImageUrl) {
        return new CountryStatisticAdminDto(
                id,
                countryId,
                countryCode,
                countryName,
                source,
                indicatorCode,
                indicatorName,
                category,
                value,
                valueText,
                unit,
                year,
                globalRank,
                regionalRank,
                percentile,
                deviationScore,
                isCurrent,
                manualOverride,
                confidence,
                weight,
                fetchedAt,
                resolvedImageUrl,
                originalImageUrl,
                imageSourcePageUrl,
                imageAuthorName,
                imageAttributionText,
                imageLicenseLabel,
                imageLicenseUrl);
    }
}
