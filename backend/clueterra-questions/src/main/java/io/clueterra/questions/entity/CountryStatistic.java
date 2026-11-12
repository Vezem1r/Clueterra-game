package io.clueterra.questions.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
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
public class CountryStatistic {

    private Long id;
    private Long countryId;
    
    private DataSource source;
    private String indicatorCode;
    private String indicatorName;
    private Category category;
    
    private BigDecimal value;
    private String valueText;
    private String unit;
    private Integer year;
    private String imageUrl;
    private String originalImageUrl;
    private String imageSourcePageUrl;
    private String imageAuthorName;
    private String imageAttributionText;
    private String imageLicenseLabel;
    private String imageLicenseUrl;
    private Long imageResourceId;

    private Integer globalRank;
    private Integer regionalRank;
    private BigDecimal percentile;
    private BigDecimal deviationScore;
    
    @Builder.Default
    private Boolean isCurrent = true;

    @Builder.Default
    private Boolean isDynamic = false;

    @Builder.Default
    private BigDecimal confidence = BigDecimal.ONE;

    @Builder.Default
    private BigDecimal weight = BigDecimal.valueOf(0.5);

    private Instant fetchedAt;
    private LocalDate effectiveDate;

    private Country country;
}
