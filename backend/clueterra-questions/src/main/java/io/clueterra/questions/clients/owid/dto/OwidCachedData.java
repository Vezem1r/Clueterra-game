package io.clueterra.questions.clients.owid.dto;

import io.clueterra.questions.entity.CountryStatistic;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record OwidCachedData(
        Map<Long, Map<String, CountryStatistic>> byCountry,
        Map<String, List<BigDecimal>> sortedDesc,
        Map<String, BigDecimal> globalAvg,
        Map<String, Map<Long, Integer>> ranks,
        int totalCountries
) {
}
