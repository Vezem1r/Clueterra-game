package io.clueterra.questions.clients.openstreetmap.dto;

import io.clueterra.questions.entity.enums.Category;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class OsmCountryStatistic {
    private String countryCode;
    private String indicatorCode;
    private Category category;
    private BigDecimal value;
    private String valueText;
    private BigDecimal weight;
    private Instant fetchedAt;
}
