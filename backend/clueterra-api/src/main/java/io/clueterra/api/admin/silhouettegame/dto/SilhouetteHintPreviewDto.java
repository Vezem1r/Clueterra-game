package io.clueterra.api.admin.silhouettegame.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record SilhouetteHintPreviewDto(int position, String kind, int costUnits, JsonNode payload) {
}
