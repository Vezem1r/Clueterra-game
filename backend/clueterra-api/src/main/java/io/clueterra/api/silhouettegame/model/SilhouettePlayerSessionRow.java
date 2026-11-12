package io.clueterra.api.silhouettegame.model;

import java.util.List;

import io.clueterra.api.silhouettegame.dto.SilhouettePlayerStatsDto;

public record SilhouettePlayerSessionRow(SilhouettePlayerStatsDto stats, List<String> solved) {
}
