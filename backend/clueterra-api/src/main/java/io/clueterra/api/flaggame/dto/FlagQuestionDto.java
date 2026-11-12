package io.clueterra.api.flaggame.dto;

import java.util.List;

public record FlagQuestionDto(
        long round,
        int tier,
        String flagUrl,
        FlagHintsDto hints,
        List<FlagClueDto> clues,
        int hintsUsed,
        int attempts,
        int maximumXp,
        int baseXp,
        List<String> misses) {
}
