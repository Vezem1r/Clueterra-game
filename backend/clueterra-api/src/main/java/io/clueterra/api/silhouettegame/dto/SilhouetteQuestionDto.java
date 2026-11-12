package io.clueterra.api.silhouettegame.dto;

import java.util.List;

public record SilhouetteQuestionDto(
        long round,
        int tier,
        String shapePath,
        SilhouetteHintsDto hints,
        List<SilhouetteClueDto> clues,
        int hintsUsed,
        int attempts,
        int maximumXp,
        int baseXp,
        List<SilhouetteMissDto> misses) {
}
