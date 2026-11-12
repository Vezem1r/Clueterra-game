package io.clueterra.questions.ai.service.change;

import io.clueterra.questions.ai.service.generation.AiWikimediaAttribution;
import io.clueterra.questions.entity.Hint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AiApplyBatch {

    private final Map<Long, Optional<AiWikimediaAttribution>> attributionsByProposalId;
    private final List<Hint> embeddingTargets = new ArrayList<>();

    AiApplyBatch(Map<Long, Optional<AiWikimediaAttribution>> attributionsByProposalId) {
        this.attributionsByProposalId = attributionsByProposalId;
    }

    static AiApplyBatch withoutAttributions() {
        return new AiApplyBatch(Map.of());
    }

    Optional<Optional<AiWikimediaAttribution>> prefetchedAttribution(Long proposalId) {
        return Optional.ofNullable(attributionsByProposalId.get(proposalId));
    }

    void storeEmbeddingAfterCommit(Hint hint) {
        embeddingTargets.add(hint);
    }

    List<Hint> embeddingTargets() {
        return List.copyOf(embeddingTargets);
    }
}
