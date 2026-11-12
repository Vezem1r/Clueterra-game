package io.clueterra.questions.service.hint.lens;

import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import java.math.BigDecimal;

public class CandidateHintBuilder {
    String text;
    HintType type;
    Category category;
    DataSource source;
    String sourceIndicator;
    Integer sourceYear;
    int estimatedDifficulty = 50;
    BigDecimal estimatedQuality = BigDecimal.valueOf(0.5);
    int ambiguityCount = 50;
    String lensName;
    String imageUrl;
    String originalImageUrl;
    String imageSourcePageUrl;
    String imageAuthorName;
    String imageAttributionText;
    String imageLicenseLabel;
    String imageLicenseUrl;

    public CandidateHintBuilder text(String text) {
        this.text = text;
        return this;
    }

    public CandidateHintBuilder type(HintType type) {
        this.type = type;
        return this;
    }

    public CandidateHintBuilder category(Category category) {
        this.category = category;
        return this;
    }

    public CandidateHintBuilder source(DataSource source) {
        this.source = source;
        return this;
    }

    public CandidateHintBuilder sourceIndicator(String indicator) {
        this.sourceIndicator = indicator;
        return this;
    }

    public CandidateHintBuilder sourceYear(Integer year) {
        this.sourceYear = year;
        return this;
    }

    public CandidateHintBuilder estimatedDifficulty(int diff) {
        this.estimatedDifficulty = Math.max(1, Math.min(100, diff));
        return this;
    }

    public CandidateHintBuilder estimatedQuality(BigDecimal quality) {
        this.estimatedQuality = quality;
        return this;
    }

    public CandidateHintBuilder ambiguityCount(int count) {
        this.ambiguityCount = count;
        return this;
    }

    public CandidateHintBuilder lensName(String name) {
        this.lensName = name;
        return this;
    }

    public CandidateHintBuilder imageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
        return this;
    }

    public CandidateHintBuilder originalImageUrl(String originalImageUrl) {
        this.originalImageUrl = originalImageUrl;
        return this;
    }

    public CandidateHintBuilder imageSourcePageUrl(String imageSourcePageUrl) {
        this.imageSourcePageUrl = imageSourcePageUrl;
        return this;
    }

    public CandidateHintBuilder imageAuthorName(String imageAuthorName) {
        this.imageAuthorName = imageAuthorName;
        return this;
    }

    public CandidateHintBuilder imageAttributionText(String imageAttributionText) {
        this.imageAttributionText = imageAttributionText;
        return this;
    }

    public CandidateHintBuilder imageLicenseLabel(String imageLicenseLabel) {
        this.imageLicenseLabel = imageLicenseLabel;
        return this;
    }

    public CandidateHintBuilder imageLicenseUrl(String imageLicenseUrl) {
        this.imageLicenseUrl = imageLicenseUrl;
        return this;
    }

    public CandidateHint build() {
        return new CandidateHint(
                text, type, category, source, sourceIndicator, sourceYear,
                estimatedDifficulty, estimatedQuality, ambiguityCount, lensName, imageUrl, originalImageUrl,
                imageSourcePageUrl, imageAuthorName, imageAttributionText, imageLicenseLabel, imageLicenseUrl
        );
    }
}
