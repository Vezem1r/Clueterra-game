package io.clueterra.api.rivergame.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;

public record RiverQuestionDto(
        long id,
        String difficulty,
        String imageUrl,
        @JsonRawValue String course,
        @JsonRawValue String revealedHints,
        int attempts,
        int hintsUsed,
        int maximumXp,
        int cluePenaltyXp,
        int baseXp,
        long roundNumber) {
}
