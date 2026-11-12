package io.clueterra.questions.service.sync;

import io.clueterra.questions.clients.common.dto.FetchFailure;
import io.clueterra.questions.clients.common.dto.RawCountryData;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record StaleCleanupGuard(boolean blocksAll, Set<String> failedCountryCodes) {

    static StaleCleanupGuard from(List<FetchFailure> fetchFailures) {
        boolean global = fetchFailures.stream().anyMatch(failure -> failure.countryCode() == null);
        Set<String> countries = fetchFailures.stream()
                .map(FetchFailure::countryCode)
                .filter(Objects::nonNull)
                .map(code -> code.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        return new StaleCleanupGuard(global, countries);
    }

    boolean allowsCleanup(RawCountryData data) {
        if (blocksAll || !data.isStatisticsComplete()) {
            return false;
        }
        return !matches(data.getIsoAlpha2()) && !matches(data.getIsoAlpha3());
    }

    private boolean matches(String code) {
        return code != null && failedCountryCodes.contains(code.toUpperCase(Locale.ROOT));
    }
}
