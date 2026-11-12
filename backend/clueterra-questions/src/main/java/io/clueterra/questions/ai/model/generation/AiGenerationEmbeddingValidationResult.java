package io.clueterra.questions.ai.model.generation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record AiGenerationEmbeddingValidationResult(
        List<AiGenerationEmbeddingAssessment> assessments) {

    public Map<Integer, AiGenerationEmbeddingAssessment> byProposalIndex() {
        return assessments.stream().collect(Collectors.toMap(
                AiGenerationEmbeddingAssessment::proposalIndex,
                Function.identity(),
                (left, right) -> right,
                LinkedHashMap::new));
    }

    public List<AiGenerationEmbeddingAssessment> conflicts() {
        return assessments.stream()
                .filter(AiGenerationEmbeddingAssessment::conflict)
                .toList();
    }

    public boolean hasUnchecked() {
        return assessments.stream().anyMatch(assessment -> !assessment.checked());
    }
}
