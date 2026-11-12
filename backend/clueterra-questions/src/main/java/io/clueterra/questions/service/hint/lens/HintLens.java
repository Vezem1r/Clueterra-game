package io.clueterra.questions.service.hint.lens;

import io.clueterra.questions.entity.enums.DataSource;

import java.util.List;
import java.util.Set;

public interface HintLens {

    String name();

    boolean canApply(StatisticContext context);

    List<CandidateHint> generate(StatisticContext context);

    default LensType lensType() {
        return LensType.GENERIC;
    }

    default Set<String> claimedPrefixes() {
        return Set.of();
    }

    default Set<String> claimedIndicators() {
        return Set.of();
    }

    default Set<DataSource> claimedSources() {
        return Set.of();
    }

    default boolean ownsIndicator(DataSource source, String indicatorCode) {
        if (indicatorCode == null) {
            return false;
        }

        boolean prefixMatch = claimedPrefixes().stream().anyMatch(indicatorCode::startsWith);
        boolean exactMatch = claimedIndicators().contains(indicatorCode);
        if (!prefixMatch && !exactMatch) {
            return false;
        }

        Set<DataSource> scopedSources = claimedSources();
        return scopedSources.isEmpty() || (source != null && scopedSources.contains(source));
    }

    default boolean hasOwnershipRules() {
        return !claimedPrefixes().isEmpty() || !claimedIndicators().isEmpty();
    }

    /** Invalidate any internal caches so the next generate() call reads fresh data. */
    default void invalidateCache() {
    }
}
