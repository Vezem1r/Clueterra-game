package io.clueterra.questions.service.hint.lens;

import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.enums.DataSource;

import java.util.List;
import java.util.Set;

public interface CountryHintLens {

    String name();

    DataSource source();

    default boolean isAvailable() {
        return true;
    }

    default boolean canApply(Country country) {
        return true;
    }

    List<CandidateHint> generate(Country country);

    default Set<String> claimedPrefixes() {
        return Set.of();
    }

    default Set<DataSource> cleanupSources() {
        return Set.of(source());
    }

    default boolean shouldCleanup(Country country) {
        return canApply(country);
    }
}
