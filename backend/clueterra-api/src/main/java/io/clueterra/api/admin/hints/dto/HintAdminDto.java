package io.clueterra.api.admin.hints.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record HintAdminDto(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long countryId,
        String countryCode,
        String countryName,
        String category,
        String hintType,
        String hintText,
        String imageUrl,
        String originalImageUrl,
        String imageSourcePageUrl,
        String imageAuthorName,
        String imageAttributionText,
        String imageLicenseLabel,
        String imageLicenseUrl,
        Integer difficulty,
        BigDecimal qualityScore,
        String source,
        String sourceIndicator,
        Integer sourceYear,
        Integer ambiguityCount,
        Integer timesUsed,
        Integer timesCorrect,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Instant reviewRetryAfterAt,
        Boolean editorialTextLocked) {

    public HintAdminDto(Long id, Long countryId, String countryCode, String countryName,
                        String category, String hintType, String hintText, String imageUrl,
                        String originalImageUrl, String imageSourcePageUrl, String imageAuthorName,
                        String imageAttributionText, String imageLicenseLabel, String imageLicenseUrl,
                        Integer difficulty, BigDecimal qualityScore, String source, String sourceIndicator,
                        Integer sourceYear, Integer ambiguityCount, Integer timesUsed, Integer timesCorrect,
                        Boolean isActive, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, countryId, countryCode, countryName, category, hintType, hintText, imageUrl,
                originalImageUrl, imageSourcePageUrl, imageAuthorName, imageAttributionText,
                imageLicenseLabel, imageLicenseUrl, difficulty, qualityScore, source, sourceIndicator,
                sourceYear, ambiguityCount, timesUsed, timesCorrect, isActive, createdAt, updatedAt,
                null, false);
    }
}
