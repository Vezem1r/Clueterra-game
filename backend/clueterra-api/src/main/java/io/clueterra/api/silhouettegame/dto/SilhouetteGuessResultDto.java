package io.clueterra.api.silhouettegame.dto;

import java.util.List;

public record SilhouetteGuessResultDto(boolean correct, int attempts, int xpAwarded, SilhouetteRevealDto reveal,
        SilhouetteMissDto miss, List<SilhouetteMissDto> misses, SilhouettePlayerStatsDto stats) {
}
