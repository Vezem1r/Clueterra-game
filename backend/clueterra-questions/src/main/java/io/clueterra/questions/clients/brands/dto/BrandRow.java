package io.clueterra.questions.clients.brands.dto;

import io.clueterra.questions.clients.common.CountryRow;
import java.time.Instant;

public record BrandRow(
        String countryCode,
        String brandName,
        String industry,
        Integer foundedYear,
        int globalRecognition,
        int rank,
        boolean lastFetched,
        Instant fetchTimestamp
) implements CountryRow {
}
