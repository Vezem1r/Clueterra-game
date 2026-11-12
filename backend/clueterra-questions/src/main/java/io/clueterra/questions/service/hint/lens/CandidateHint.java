package io.clueterra.questions.service.hint.lens;

import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;

import java.math.BigDecimal;

public record CandidateHint(
        String text,
        HintType type,
        Category category,
        DataSource source,
        String sourceIndicator,
        Integer sourceYear,
        int estimatedDifficulty,
        BigDecimal estimatedQuality,
        int ambiguityCount,
        String lensName,
        String imageUrl,
        String originalImageUrl,
        String imageSourcePageUrl,
        String imageAuthorName,
        String imageAttributionText,
        String imageLicenseLabel,
        String imageLicenseUrl
) {
    public static CandidateHintBuilder builder() {
        return new CandidateHintBuilder();
    }

    public CandidateHint withImageMetadata(
            String originalImageUrl,
            String imageSourcePageUrl,
            String imageAuthorName,
            String imageAttributionText,
            String imageLicenseLabel,
            String imageLicenseUrl) {
        return new CandidateHint(
                text,
                type,
                category,
                source,
                sourceIndicator,
                sourceYear,
                estimatedDifficulty,
                estimatedQuality,
                ambiguityCount,
                lensName,
                imageUrl,
                originalImageUrl != null ? originalImageUrl : this.originalImageUrl,
                imageSourcePageUrl != null ? imageSourcePageUrl : this.imageSourcePageUrl,
                imageAuthorName != null ? imageAuthorName : this.imageAuthorName,
                imageAttributionText != null ? imageAttributionText : this.imageAttributionText,
                imageLicenseLabel != null ? imageLicenseLabel : this.imageLicenseLabel,
                imageLicenseUrl != null ? imageLicenseUrl : this.imageLicenseUrl
        );
    }

    public CandidateHint withOriginalImageUrl(String originalImageUrl) {
        return withImageMetadata(originalImageUrl, null, null, null, null, null);
    }
}
