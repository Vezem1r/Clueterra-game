package io.clueterra.questions.silhouettegame.model;

import com.fasterxml.jackson.databind.JsonNode;

public record SilhouetteHintDraft(int position, String kind, int costUnits, JsonNode payload) {
}
