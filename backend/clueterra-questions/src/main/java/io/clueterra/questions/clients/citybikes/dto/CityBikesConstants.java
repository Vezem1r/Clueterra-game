package io.clueterra.questions.clients.citybikes.dto;

import java.math.BigDecimal;

public final class CityBikesConstants {

    private CityBikesConstants() {
    }

    public static final int DEFAULT_DETAIL_LIMIT_PER_COUNTRY = 3;
    public static final BigDecimal CONFIDENCE = BigDecimal.valueOf(0.74);
    public static final BigDecimal WEIGHT = BigDecimal.valueOf(0.78);
}
