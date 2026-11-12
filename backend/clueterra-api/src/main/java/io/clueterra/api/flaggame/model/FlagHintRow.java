package io.clueterra.api.flaggame.model;

import com.fasterxml.jackson.databind.JsonNode;

public record FlagHintRow(int position, String kind, int costUnits, JsonNode payload) {
}
