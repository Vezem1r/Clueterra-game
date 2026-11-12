package io.clueterra.budget.source.wikidata;

import java.util.ArrayList;
import java.util.List;

import io.clueterra.budget.project.WikidataCandidate;

public record WikidataBatchOutcome(
        List<WikidataCandidate> candidates,
        int processedCount,
        int deferredCount,
        List<String> failures) {

    public WikidataBatchOutcome {
        candidates = List.copyOf(candidates);
        failures = List.copyOf(failures);
    }

    public static WikidataBatchOutcome processed(
            List<WikidataCandidate> candidates, int processedCount) {
        return new WikidataBatchOutcome(candidates, processedCount, 0, List.of());
    }

    public static WikidataBatchOutcome deferred(int deferredCount, String failure) {
        return new WikidataBatchOutcome(List.of(), 0, deferredCount, List.of(failure));
    }

    public WikidataBatchOutcome merge(WikidataBatchOutcome other) {
        List<WikidataCandidate> mergedCandidates = new ArrayList<>(candidates);
        mergedCandidates.addAll(other.candidates());
        List<String> mergedFailures = new ArrayList<>(failures);
        mergedFailures.addAll(other.failures());
        return new WikidataBatchOutcome(
                mergedCandidates,
                processedCount + other.processedCount(),
                deferredCount + other.deferredCount(),
                mergedFailures);
    }
}
