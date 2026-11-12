package io.clueterra.questions.ai.model.review;

import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintReviewStatus;
import io.clueterra.questions.entity.enums.HintType;

import java.math.BigDecimal;
import java.time.Instant;

public record AiReviewCandidate(
        Long hintId,
        Long countryId,
        String countryCode,
        String countryName,
        String countryRegion,
        String countrySubregion,
        String countryCapital,
        BigDecimal difficultyModifier,
        Category category,
        HintType hintType,
        String hintText,
        String imageUrl,
        String originalImageUrl,
        Integer difficulty,
        BigDecimal qualityScore,
        DataSource source,
        String sourceIndicator,
        Integer sourceYear,
        String contentRevisionHash,
        HintReviewStatus reviewStatus,
        Instant updatedAt,
        Instant lastReviewedAt) {

    public AiReviewCandidate(
            Long hintId,
            Long countryId,
            String countryCode,
            String countryName,
            BigDecimal difficultyModifier,
            String hintText,
            String imageUrl,
            String originalImageUrl,
            Integer difficulty,
            BigDecimal qualityScore,
            DataSource source,
            String sourceIndicator,
            Integer sourceYear,
            String contentRevisionHash,
            HintReviewStatus reviewStatus,
            Instant updatedAt,
            Instant lastReviewedAt) {
        this(
                hintId, countryId, countryCode, countryName, null, null, null,
                difficultyModifier, null, null, hintText, imageUrl, originalImageUrl, difficulty,
                qualityScore, source, sourceIndicator, sourceYear, contentRevisionHash,
                reviewStatus, updatedAt, lastReviewedAt);
    }

    public AiReviewCandidate(
            Long hintId,
            Long countryId,
            String countryCode,
            String countryName,
            String countryRegion,
            String countrySubregion,
            String countryCapital,
            BigDecimal difficultyModifier,
            String hintText,
            String imageUrl,
            String originalImageUrl,
            Integer difficulty,
            BigDecimal qualityScore,
            DataSource source,
            String sourceIndicator,
            Integer sourceYear,
            String contentRevisionHash,
            HintReviewStatus reviewStatus,
            Instant updatedAt,
            Instant lastReviewedAt) {
        this(
                hintId, countryId, countryCode, countryName, countryRegion, countrySubregion, countryCapital,
                difficultyModifier, null, null, hintText, imageUrl, originalImageUrl, difficulty,
                qualityScore, source, sourceIndicator, sourceYear, contentRevisionHash,
                reviewStatus, updatedAt, lastReviewedAt);
    }

    public boolean hasImage() {
        return (imageUrl != null && !imageUrl.isBlank())
                || (originalImageUrl != null && !originalImageUrl.isBlank());
    }
}
