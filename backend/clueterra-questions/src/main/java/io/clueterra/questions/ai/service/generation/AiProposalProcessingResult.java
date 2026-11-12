package io.clueterra.questions.ai.service.generation;

import io.clueterra.questions.ai.model.changeset.AiChangeSet;

import java.util.Map;

public record AiProposalProcessingResult(
        int generatedItems,
        int changedItems,
        int duplicateItems,
        int rejectedItems,
        Map<String, Integer> generatedByCountry,
        AiChangeSet changeSet
) {
}
