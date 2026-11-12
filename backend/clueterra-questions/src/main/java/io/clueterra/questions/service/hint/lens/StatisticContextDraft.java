package io.clueterra.questions.service.hint.lens;

import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.CountryStatistic;
import java.math.BigDecimal;
import java.util.Optional;

public class StatisticContextDraft {
    Country country;
    CountryStatistic statistic;
    int totalCountriesWithData;
    int totalActiveCountries;
    String indicatorDisplayName;
    boolean isHigherBetter = true;
    String topCountryName;
    String bottomCountryName;
    BigDecimal topValue;
    BigDecimal bottomValue;
    String regionalTopCountryName;
    Integer regionalTopRank;

    public StatisticContextDraft country(Country country) {
        this.country = country;
        return this;
    }

    public StatisticContextDraft statistic(CountryStatistic statistic) {
        this.statistic = statistic;
        return this;
    }

    public StatisticContextDraft totalCountriesWithData(int count) {
        this.totalCountriesWithData = count;
        return this;
    }

    public StatisticContextDraft totalActiveCountries(int count) {
        this.totalActiveCountries = count;
        return this;
    }

    public StatisticContextDraft indicatorDisplayName(String name) {
        this.indicatorDisplayName = name;
        return this;
    }

    public StatisticContextDraft isHigherBetter(boolean value) {
        this.isHigherBetter = value;
        return this;
    }

    public StatisticContextDraft topCountryName(String name) {
        this.topCountryName = name;
        return this;
    }

    public StatisticContextDraft bottomCountryName(String name) {
        this.bottomCountryName = name;
        return this;
    }

    public StatisticContextDraft topValue(BigDecimal value) {
        this.topValue = value;
        return this;
    }

    public StatisticContextDraft bottomValue(BigDecimal value) {
        this.bottomValue = value;
        return this;
    }

    public StatisticContextDraft regionalTopCountryName(String name) {
        this.regionalTopCountryName = name;
        return this;
    }

    public StatisticContextDraft regionalTopRank(Integer rank) {
        this.regionalTopRank = rank;
        return this;
    }

    public StatisticContext build() {
        return new StatisticContext(
                country,
                statistic,
                totalCountriesWithData,
                totalActiveCountries,
                indicatorDisplayName,
                isHigherBetter,
                Optional.ofNullable(topCountryName),
                Optional.ofNullable(bottomCountryName),
                Optional.ofNullable(topValue),
                Optional.ofNullable(bottomValue),
                Optional.ofNullable(regionalTopCountryName),
                Optional.ofNullable(regionalTopRank)
        );
    }
}
