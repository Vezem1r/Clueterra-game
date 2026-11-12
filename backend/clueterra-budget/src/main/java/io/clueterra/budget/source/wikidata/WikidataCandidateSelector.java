package io.clueterra.budget.source.wikidata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import io.clueterra.budget.config.WikidataProperties;
import io.clueterra.budget.project.BudgetDifficulty;
import io.clueterra.budget.project.CandidateRegion;
import io.clueterra.budget.project.WikidataCandidate;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WikidataCandidateSelector {

    private static final Comparator<WikidataCandidate> QUALITY_ORDER =
            Comparator.comparingInt(WikidataCandidate::qualityScore)
                    .reversed()
                    .thenComparing(Comparator.comparingInt(WikidataCandidate::monthlyPageviews).reversed())
                    .thenComparing(Comparator.comparingInt(WikidataCandidate::sitelinkCount).reversed())
                    .thenComparing(WikidataCandidate::wikidataId);

    private final WikidataProperties properties;

    public List<WikidataCandidate> select(List<WikidataCandidate> candidates) {
        List<WikidataCandidate> sorted = candidates.stream().sorted(QUALITY_ORDER).toList();
        int target = sorted.size();
        List<WikidataCandidate> qualityRankedSelection =
                selectBalancedSubset(sorted, target).stream().sorted(QUALITY_ORDER).toList();
        int easyCount = percentageCount(target, properties.easySharePercent());
        int mediumCount = percentageCount(target, properties.mediumSharePercent());
        Set<String> easyIds = candidateIds(selectBalancedSubset(qualityRankedSelection, easyCount));
        List<WikidataCandidate> remainingAfterEasy =
                qualityRankedSelection.stream()
                        .filter(candidate -> !easyIds.contains(candidate.wikidataId()))
                        .toList();
        Set<String> mediumIds = candidateIds(selectBalancedSubset(remainingAfterEasy, mediumCount));
        return IntStream.range(0, qualityRankedSelection.size())
                .mapToObj(
                        index -> {
                            WikidataCandidate candidate = qualityRankedSelection.get(index);
                            BudgetDifficulty difficulty = difficulty(candidate, easyIds, mediumIds);
                            return candidate.withCatalogMetadata(difficulty, candidate.qualityScore(), index + 1);
                        })
                .toList();
    }

    private List<WikidataCandidate> selectBalancedSubset(List<WikidataCandidate> sorted, int target) {
        int countryLimit = shareLimit(target, properties.maximumCountrySharePercent());
        int regionLimit = shareLimit(target, properties.maximumRegionSharePercent());
        List<WikidataCandidate> selected = new ArrayList<>(target);
        Set<String> selectedIds = new LinkedHashSet<>();
        Map<String, Integer> countryCounts = new HashMap<>();
        Map<CandidateRegion, Integer> regionCounts = new EnumMap<>(CandidateRegion.class);

        selectPass(
                sorted,
                selected,
                selectedIds,
                target,
                candidate ->
                        countryCounts.getOrDefault(candidate.countryCode(), 0) < countryLimit
                                && regionCounts.getOrDefault(candidate.region(), 0) < regionLimit,
                countryCounts,
                regionCounts);
        selectPass(
                sorted,
                selected,
                selectedIds,
                target,
                candidate -> countryCounts.getOrDefault(candidate.countryCode(), 0) < countryLimit,
                countryCounts,
                regionCounts);
        selectPass(
                sorted, selected, selectedIds, target, candidate -> true, countryCounts, regionCounts);
        return List.copyOf(selected);
    }

    private void selectPass(
            List<WikidataCandidate> sorted,
            List<WikidataCandidate> selected,
            Set<String> selectedIds,
            int target,
            Predicate<WikidataCandidate> predicate,
            Map<String, Integer> countryCounts,
            Map<CandidateRegion, Integer> regionCounts) {
        sorted.stream()
                .filter(candidate -> selected.size() < target)
                .filter(candidate -> !selectedIds.contains(candidate.wikidataId()))
                .filter(predicate)
                .forEach(
                        candidate -> {
                            selected.add(candidate);
                            selectedIds.add(candidate.wikidataId());
                            countryCounts.merge(candidate.countryCode(), 1, Integer::sum);
                            regionCounts.merge(candidate.region(), 1, Integer::sum);
                        });
    }

    private BudgetDifficulty difficulty(
            WikidataCandidate candidate, Set<String> easyIds, Set<String> mediumIds) {
        if (easyIds.contains(candidate.wikidataId())) {
            return BudgetDifficulty.EASY;
        }
        if (mediumIds.contains(candidate.wikidataId())) {
            return BudgetDifficulty.MEDIUM;
        }
        return BudgetDifficulty.HARD;
    }

    private int shareLimit(int total, int percentage) {
        return Math.max(1, (int) Math.ceil(total * percentage / 100.0));
    }

    private int percentageCount(int total, int percentage) {
        return Math.max(1, (int) Math.round(total * percentage / 100.0));
    }

    private Set<String> candidateIds(List<WikidataCandidate> candidates) {
        return candidates.stream()
                .map(WikidataCandidate::wikidataId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
