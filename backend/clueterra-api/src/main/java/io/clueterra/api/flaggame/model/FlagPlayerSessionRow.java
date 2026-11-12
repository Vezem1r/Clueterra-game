package io.clueterra.api.flaggame.model;

import java.util.List;

import io.clueterra.api.flaggame.dto.FlagPlayerStatsDto;

public record FlagPlayerSessionRow(FlagPlayerStatsDto stats, List<String> solved) {
}
