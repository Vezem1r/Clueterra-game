package io.clueterra.questions.clients.common.dto;

import io.clueterra.questions.entity.enums.DataSource;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class RawCountryData {
    @Getter
    private final String isoAlpha2;
    @Getter
    private final String isoAlpha3;
    @Getter
    private final String nameEn;
    private final String nameNative;
    private final String region;
    private final String subregion;
    private final String capital;
    private final String flagEmoji;
    private final String flagUrl;
    private final Double latitude;
    private final Double longitude;
    private final BigDecimal area;
    private final Boolean landlocked;
    @Getter
    private final List<String> continents;
    @Getter
    private final List<String> languages;
    @Getter
    private final List<String> currencies;
    private final Boolean independent;
    @Getter
    private final DataSource source;
    @Getter
    private final Instant fetchedAt;
    @Getter
    private final List<RawStatistic> statistics;
    @Getter
    private final boolean statisticsComplete;

    RawCountryData(RawCountryDataBuilder builder) {
        this.isoAlpha2 = builder.isoAlpha2;
        this.isoAlpha3 = builder.isoAlpha3;
        this.nameEn = builder.nameEn;
        this.nameNative = builder.nameNative;
        this.region = builder.region;
        this.subregion = builder.subregion;
        this.capital = builder.capital;
        this.flagEmoji = builder.flagEmoji;
        this.flagUrl = builder.flagUrl;
        this.latitude = builder.latitude;
        this.longitude = builder.longitude;
        this.area = builder.area;
        this.landlocked = builder.landlocked;
        this.continents = builder.continents != null ? List.copyOf(builder.continents) : List.of();
        this.languages = builder.languages != null ? List.copyOf(builder.languages) : List.of();
        this.currencies = builder.currencies != null ? List.copyOf(builder.currencies) : List.of();
        this.independent = builder.independent;
        this.source = builder.source;
        this.fetchedAt = builder.fetchedAt != null ? builder.fetchedAt : Instant.now();
        this.statistics = builder.statistics != null ? List.copyOf(builder.statistics) : List.of();
        this.statisticsComplete = builder.statisticsComplete;
    }

    public static RawCountryDataBuilder builder() {
        return new RawCountryDataBuilder();
    }

    public Optional<String> getNameNative() {
        return Optional.ofNullable(nameNative);
    }

    public Optional<String> getRegion() {
        return Optional.ofNullable(region);
    }

    public Optional<String> getSubregion() {
        return Optional.ofNullable(subregion);
    }

    public Optional<String> getCapital() {
        return Optional.ofNullable(capital);
    }

    public Optional<String> getFlagEmoji() {
        return Optional.ofNullable(flagEmoji);
    }

    public Optional<String> getFlagUrl() {
        return Optional.ofNullable(flagUrl);
    }

    public Optional<Double> getLatitude() {
        return Optional.ofNullable(latitude);
    }

    public Optional<Double> getLongitude() {
        return Optional.ofNullable(longitude);
    }

    public Optional<BigDecimal> getArea() {
        return Optional.ofNullable(area);
    }

    public Optional<Boolean> getLandlocked() {
        return Optional.ofNullable(landlocked);
    }

    public Optional<Boolean> getIndependent() {
        return Optional.ofNullable(independent);
    }

    public String getPreferredIsoCode() {
        return isoAlpha2 != null ? isoAlpha2 : isoAlpha3;
    }

    public boolean isValid() {
        return (isoAlpha2 != null || isoAlpha3 != null) && nameEn != null && source != null;
    }

    @Override
    public String toString() {
        return "RawCountryData{" +
                "iso=" + getPreferredIsoCode() +
                ", name='" + nameEn + '\'' +
                ", source=" + source +
                ", statistics=" + statistics.size() +
                '}';
    }
}
