package io.clueterra.questions.clients.nagerdate.hintgeneration;

import io.clueterra.questions.entity.CountryStatistic;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class NagerDateAmbiguityCalculator {

    private NagerDateAmbiguityCalculator() {
    }

    public static int forCount(Long countryId, Map<Long, List<CountryStatistic>> allData, int count) {
        return (int) allData.entrySet().stream()
                .filter(e -> !e.getKey().equals(countryId))
                .filter(e -> e.getValue().stream()
                        .anyMatch(s -> "HOLIDAY_COUNT".equals(s.getIndicatorCode())
                                && s.getValue() != null
                                && Math.abs(s.getValue().intValue() - count) <= 2))
                .count();
    }

    public static int forNotable(Long countryId, Map<Long, List<CountryStatistic>> allData, String holidayName) {
        if (holidayName == null || holidayName.isBlank()) return 50;
        String normalised = holidayName.trim().toLowerCase();
        return (int) allData.entrySet().stream()
                .filter(e -> !e.getKey().equals(countryId))
                .filter(e -> e.getValue().stream()
                        .anyMatch(s -> "HOLIDAY_NOTABLE".equals(s.getIndicatorCode())
                                && s.getValueText() != null
                                && Arrays.stream(s.getValueText().split(","))
                                .map(h -> h.trim().toLowerCase())
                                .anyMatch(normalised::equals)))
                .count();
    }

    public static int forUniqueEn(Long countryId, Map<Long, List<CountryStatistic>> allData, String englishName) {
        if (englishName == null || englishName.isBlank()) return 50;
        String normalised = englishName.trim().toLowerCase();
        return (int) allData.entrySet().stream()
                .filter(e -> !e.getKey().equals(countryId))
                .filter(e -> e.getValue().stream()
                        .anyMatch(s -> "HOLIDAY_UNIQUE_EN".equals(s.getIndicatorCode())
                                && s.getValueText() != null
                                && Arrays.stream(s.getValueText().split(","))
                                .map(h -> h.trim().toLowerCase())
                                .anyMatch(normalised::equals)))
                .count();
    }
}
