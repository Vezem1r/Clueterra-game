package io.clueterra.questions.clients.openstreetmap.dto;

import io.clueterra.questions.entity.CountryStatistic;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record OsmCachedData(
        Map<Long, List<CountryStatistic>> byCountry,
        Map<String, Map<Long, Integer>> ranksByIndicator,
        Instant fetchedAt) {

    private static final Duration TTL = Duration.ofHours(1);

    public boolean isExpired() {
        return Instant.now().isAfter(fetchedAt.plus(TTL));
    }
}
