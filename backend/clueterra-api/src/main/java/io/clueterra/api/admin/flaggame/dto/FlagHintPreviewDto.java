package io.clueterra.api.admin.flaggame.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record FlagHintPreviewDto(int position, String kind, int costUnits, JsonNode payload) {
}
