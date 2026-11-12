package io.clueterra.questions.service.hint;

import io.clueterra.questions.service.hint.lens.CandidateHint;

import java.util.List;

public record SourceOwnedLifecycleResult(List<CandidateHint> accepted, int newCount, int updatedCount) {
    public static SourceOwnedLifecycleResult empty() {
        return new SourceOwnedLifecycleResult(List.of(), 0, 0);
    }
}
