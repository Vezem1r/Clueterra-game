package io.clueterra.questions.clients.bigmac.hintgeneration;

import io.clueterra.questions.entity.CountryStatistic;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record BigMacCachedData(
        Map<Long, List<CountryStatistic>> byCountry,
        List<BigDecimal> sortedPrices,
        List<BigDecimal> allValuations,
        BigDecimal globalAvg,
        int totalCountries,
        Map<String, List<BigDecimal>> pricesByRegion,
        Map<String, BigDecimal> regionalAvg,
        BigDecimal cheapestPrice,
        BigDecimal usPrice,
        Map<Long, String> countryIdToRegion,
        Map<Long, BigDecimal> countryIdToPrice,
        Map<Long, BigDecimal> countryIdToValuation,
        List<BigDecimal> sortedValuationAbs
) {
}
