package io.clueterra.questions.ai.model.generation;

import io.clueterra.questions.ai.service.generation.AiResolvedWikimediaImage;

public record AiGenerationResolvedImageCandidate(
        int proposalIndex,
        AiHintGenerationProposalResult proposal,
        AiGenerationCountryGapCandidate country,
        AiGeneratedImageKind imageKind,
        AiResolvedWikimediaImage resolvedImage) {
}
