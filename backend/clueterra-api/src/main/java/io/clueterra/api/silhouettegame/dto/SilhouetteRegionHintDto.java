package io.clueterra.api.silhouettegame.dto;

import java.util.List;

public record SilhouetteRegionHintDto(String region, String subregion, List<Double> center) {
}
