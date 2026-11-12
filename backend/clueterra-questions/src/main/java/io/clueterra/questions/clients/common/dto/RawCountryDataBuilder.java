package io.clueterra.questions.clients.common.dto;

import io.clueterra.questions.entity.enums.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("UnusedReturnValue")
public class RawCountryDataBuilder {
    String isoAlpha2;
    String isoAlpha3;
    String nameEn;
    String nameNative;
    String region;
    String subregion;
    String capital;
    String flagEmoji;
    String flagUrl;
    Double latitude;
    Double longitude;
    BigDecimal area;
    Boolean landlocked;
    List<String> continents;
    List<String> languages;
    List<String> currencies;
    Boolean independent;
    DataSource source;
    Instant fetchedAt;
    List<RawStatistic> statistics = new ArrayList<>();
    boolean statisticsComplete = true;

    public RawCountryDataBuilder isoAlpha2(String isoAlpha2) {
        this.isoAlpha2 = normalize(isoAlpha2, 2);
        return this;
    }

    public RawCountryDataBuilder isoAlpha3(String isoAlpha3) {
        this.isoAlpha3 = normalize(isoAlpha3, 3);
        return this;
    }

    public RawCountryDataBuilder nameEn(String nameEn) {
        this.nameEn = trimToNull(nameEn);
        return this;
    }

    public RawCountryDataBuilder nameNative(String nameNative) {
        this.nameNative = trimToNull(nameNative);
        return this;
    }

    public RawCountryDataBuilder region(String region) {
        this.region = trimToNull(region);
        return this;
    }

    public RawCountryDataBuilder subregion(String subregion) {
        this.subregion = trimToNull(subregion);
        return this;
    }

    public RawCountryDataBuilder capital(String capital) {
        this.capital = trimToNull(capital);
        return this;
    }

    public RawCountryDataBuilder flagEmoji(String flagEmoji) {
        this.flagEmoji = trimToNull(flagEmoji);
        return this;
    }

    public RawCountryDataBuilder flagUrl(String flagUrl) {
        this.flagUrl = trimToNull(flagUrl);
        return this;
    }

    public RawCountryDataBuilder latitude(Double latitude) {
        this.latitude = latitude;
        return this;
    }

    public RawCountryDataBuilder longitude(Double longitude) {
        this.longitude = longitude;
        return this;
    }

    public RawCountryDataBuilder coordinates(Double lat, Double lon) {
        this.latitude = lat;
        this.longitude = lon;
        return this;
    }

    public RawCountryDataBuilder area(BigDecimal area) {
        this.area = area;
        return this;
    }

    public RawCountryDataBuilder area(Double area) {
        this.area = area != null ? BigDecimal.valueOf(area) : null;
        return this;
    }

    public RawCountryDataBuilder landlocked(Boolean landlocked) {
        this.landlocked = landlocked;
        return this;
    }

    @SuppressWarnings("unused")
    public RawCountryDataBuilder borders(List<String> borders) {
        return this;
    }

    @SuppressWarnings("unused")
    public RawCountryDataBuilder timezones(List<String> timezones) {
        return this;
    }

    public RawCountryDataBuilder continents(List<String> continents) {
        this.continents = continents;
        return this;
    }

    public RawCountryDataBuilder languages(List<String> languages) {
        this.languages = languages;
        return this;
    }

    public RawCountryDataBuilder currencies(List<String> currencies) {
        this.currencies = currencies;
        return this;
    }

    public RawCountryDataBuilder independent(Boolean independent) {
        this.independent = independent;
        return this;
    }

    @SuppressWarnings("unused")
    public RawCountryDataBuilder unMember(Boolean unMember) {
        return this;
    }

    public RawCountryDataBuilder source(DataSource source) {
        this.source = source;
        return this;
    }

    public RawCountryDataBuilder fetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
        return this;
    }

    public RawCountryDataBuilder statistics(List<RawStatistic> statistics) {
        this.statistics = statistics != null ? new ArrayList<>(statistics) : new ArrayList<>();
        return this;
    }

    public RawCountryDataBuilder addStatistic(RawStatistic statistic) {
        if (statistic != null) {
            this.statistics.add(statistic);
        }
        return this;
    }

    public RawCountryDataBuilder statisticsComplete(boolean statisticsComplete) {
        this.statisticsComplete = statisticsComplete;
        return this;
    }

    public RawCountryData build() {
        Objects.requireNonNull(source, "source is required");
        if (isoAlpha2 == null && isoAlpha3 == null) {
            throw new IllegalStateException("Either isoAlpha2 or isoAlpha3 is required");
        }
        return new RawCountryData(this);
    }

    private static String normalize(String code, int expectedLength) {
        if (code == null || code.isBlank()) return null;
        String trimmed = code.trim().toUpperCase();
        return trimmed.length() == expectedLength ? trimmed : null;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
