package io.clueterra.questions.service.hint.combinatorics.dto;

import io.clueterra.questions.entity.CountryStatistic;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record CombinatoricsSnapshot(
        List<ComboHintDefinition> definitions,
        Map<Long, Map<String, CountryStatistic>> statsByCountry,
        Map<String, Set<Long>> matchingCountriesByIndicator
) {
}
