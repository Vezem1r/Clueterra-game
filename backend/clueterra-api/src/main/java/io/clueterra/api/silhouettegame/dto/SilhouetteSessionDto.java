package io.clueterra.api.silhouettegame.dto;

import java.util.List;

public record SilhouetteSessionDto(SilhouettePlayerStatsDto stats, SilhouetteQuestionDto current, List<String> solved) {
}
