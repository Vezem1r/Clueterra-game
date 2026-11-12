package io.clueterra.api.silhouettegame.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonRawValue;

public record SilhouetteLocationHintDto(List<Double> center, @JsonRawValue String bounds) {
}
