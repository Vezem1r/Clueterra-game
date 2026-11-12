package io.clueterra.api.silhouettegame.model;

import com.fasterxml.jackson.databind.JsonNode;

public record SilhouetteHintRow(int position, String kind, int costUnits, JsonNode payload) {
}
