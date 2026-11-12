package io.clueterra.questions.service.hint.combinatorics;

import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.service.hint.combinatorics.dto.ComboHintComponent;
import io.clueterra.questions.service.hint.combinatorics.dto.ComboHintDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CombinatoricsAmbiguityCalculator {

    public int computeAmbiguity(
            ComboHintDefinition definition,
            Long countryId,
            Map<Long, Map<String, CountryStatistic>> statsByCountry,
            Set<Long> matchingCountryIds) {
        if (countryId == null || matchingCountryIds == null || matchingCountryIds.isEmpty()) {
            return 1;
        }

        String targetSignature = buildSignature(definition.components(), statsByCountry.get(countryId));
        if (targetSignature == null) {
            return Math.max(1, matchingCountryIds.size());
        }

        long sameRenderedProfileCount = matchingCountryIds.stream()
                .map(statsByCountry::get)
                .map(stats -> buildSignature(definition.components(), stats))
                .filter(targetSignature::equals)
                .count();

        return Math.max(1, (int) sameRenderedProfileCount);
    }

    private String buildSignature(
            List<ComboHintComponent> components,
            Map<String, CountryStatistic> statsByKey) {
        if (statsByKey == null || statsByKey.isEmpty()) {
            return null;
        }

        return components.stream()
                .map(component -> formatComponent(component, statsByKey))
                .collect(Collectors.collectingAndThen(Collectors.toList(), values -> {
                    if (values.stream().anyMatch(Objects::isNull)) {
                        return null;
                    }
                    return String.join("\u001F", values);
                }));
    }

    private String formatComponent(ComboHintComponent component, Map<String, CountryStatistic> statsByKey) {
        CountryStatistic statistic = statsByKey.get(component.statKey());
        if (statistic == null) {
            return component.required() ? null : component.componentKey() + "=";
        }
        if (statistic.getValue() != null) {
            return component.componentKey() + "=" + component.formatter().format(statistic.getValue());
        }
        if (statistic.getValueText() != null && !statistic.getValueText().isBlank()) {
            return component.componentKey() + "=" + statistic.getValueText().trim();
        }
        return component.required() ? null : component.componentKey() + "=";
    }
}
