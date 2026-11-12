package io.clueterra.api.silhouettegame.dto;

import java.util.List;

public record SilhouetteAnswerDto(SilhouetteRevealDto reveal, List<SilhouetteMissDto> misses, SilhouettePlayerStatsDto stats) {
}
