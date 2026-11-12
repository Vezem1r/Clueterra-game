package io.clueterra.questions.entity;

import java.math.BigDecimal;
import java.time.Instant;

import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.entity.enums.HintReviewStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hint {

    private Long id;
    private Long countryId;

    private Category category;
    private HintType hintType;
    private String hintText;
    private String imageUrl;
    private String originalImageUrl;
    private String imageSourcePageUrl;
    private String imageAuthorName;
    private String imageAttributionText;
    private String imageLicenseLabel;
    private String imageLicenseUrl;
    private Long imageResourceId;

    private Integer difficulty;
    private BigDecimal qualityScore;

    private DataSource source;
    private String sourceIndicator;
    private Integer sourceYear;

    private String contentHash;
    private Integer ambiguityCount;

    @Builder.Default
    private Integer timesUsed = 0;

    @Builder.Default
    private Integer timesCorrect = 0;

    @Builder.Default
    private Boolean isActive = true;

    private HintReviewStatus reviewStatus;
    private Instant lastReviewedAt;
    private Long lastReviewRunId;
    private Instant reviewRetryAfterAt;
    private Boolean editorialTextLocked;

    private Instant createdAt;
    private Instant updatedAt;
    private String contentRevisionHash;

    private Country country;
}
