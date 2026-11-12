package io.clueterra.questions.flaggame.model;

import com.fasterxml.jackson.databind.JsonNode;

public record FlagHintDraft(int position, String kind, int costUnits, JsonNode payload) {
}
