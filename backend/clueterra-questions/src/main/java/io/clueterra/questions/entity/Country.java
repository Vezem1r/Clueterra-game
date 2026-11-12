package io.clueterra.questions.entity;

import java.math.BigDecimal;
import java.time.Instant;

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
public class Country {

    private Long id;
    private String isoAlpha2;
    private String isoAlpha3;
    private String nameEn;
    private String nameNative;
    private String region;
    private String subregion;
    private String capital;
    private String flagEmoji;
    private String flagUrl;
    
    @Builder.Default
    private BigDecimal difficultyModifier = BigDecimal.ONE;
    
    @Builder.Default
    private Boolean isActive = true;
    
    @Builder.Default
    private Integer timesFeatured = 0;
    
    @Builder.Default
    private Integer timesGuessed = 0;
    
    private BigDecimal successRate;
    
    private Instant createdAt;
    private Instant updatedAt;
}
