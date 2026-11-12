package io.clueterra.questions.service.hint.lens;

import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.CountryStatistic;

import java.math.BigDecimal;
import java.util.Optional;

public record StatisticContext(
        Country country,
        CountryStatistic statistic,
        int totalCountriesWithData,
        int totalActiveCountries,
        String indicatorDisplayName,
        boolean isHigherBetter,
        Optional<String> topCountryName,
        Optional<String> bottomCountryName,
        Optional<BigDecimal> topValue,
        Optional<BigDecimal> bottomValue,
        Optional<String> regionalTopCountryName,
        Optional<Integer> regionalTopRank
) {
    public boolean hasGlobalRank() {
        return statistic.getGlobalRank() != null;
    }

    public boolean hasRegionalRank() {
        return statistic.getRegionalRank() != null;
    }

    public boolean hasPercentile() {
        return statistic.getPercentile() != null;
    }

    public boolean hasNumericValue() {
        return statistic.getValue() != null;
    }

    public boolean hasTextValue() {
        return statistic.getValueText() != null && !statistic.getValueText().isBlank();
    }

    public int globalRank() {
        return statistic.getGlobalRank() != null ? statistic.getGlobalRank() : 0;
    }

    public int regionalRank() {
        return statistic.getRegionalRank() != null ? statistic.getRegionalRank() : 0;
    }

    public double percentile() {
        return statistic.getPercentile() != null ? statistic.getPercentile().doubleValue() : 50.0;
    }

    public double weight() {
        return statistic.getWeight() != null ? statistic.getWeight().doubleValue() : 0.5;
    }

    public double confidence() {
        return statistic.getConfidence() != null ? statistic.getConfidence().doubleValue() : 1.0;
    }

    public boolean isDataSparse() {
        double coverage = totalActiveCountries > 0
                ? (double) totalCountriesWithData / totalActiveCountries
                : 0;
        return coverage < 0.5;
    }

    public boolean isDataCommon() {
        double coverage = totalActiveCountries > 0
                ? (double) totalCountriesWithData / totalActiveCountries
                : 0;
        return coverage > 0.8;
    }

    public boolean isTopGlobal(int threshold) {
        return hasGlobalRank() && globalRank() <= threshold;
    }

    public boolean isBottomGlobal(int threshold) {
        return hasGlobalRank() && globalRank() > totalCountriesWithData - threshold;
    }

    public boolean isTopRegional(int threshold) {
        return hasRegionalRank() && regionalRank() <= threshold;
    }

    public boolean isInTopPercentile(double threshold) {
        return hasPercentile() && percentile() >= (100 - threshold);
    }

    public boolean isInBottomPercentile(double threshold) {
        return hasPercentile() && percentile() <= threshold;
    }

    public String region() {
        return country.getRegion();
    }

    public String subregion() {
        return country.getSubregion();
    }

    public String countryName() {
        return country.getNameEn();
    }

    public String countryCode() {
        return country.getIsoAlpha2();
    }

    public static StatisticContextDraft builder() {
        return new StatisticContextDraft();
    }

}
