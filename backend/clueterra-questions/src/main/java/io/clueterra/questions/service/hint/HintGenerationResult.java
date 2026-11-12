package io.clueterra.questions.service.hint;

import java.util.Map;

public record HintGenerationResult(
        int hintsGenerated,
        int hintsNew,
        int hintsUpdated,
        int hintsRejected,
        int statisticsProcessed,
        Map<String, Object> details,
        Map<String, Object> lensBreakdown
) {
    public static HintGenerationResult empty() {
        return new HintGenerationResult(0, 0, 0, 0, 0, Map.of(), Map.of());
    }
}
