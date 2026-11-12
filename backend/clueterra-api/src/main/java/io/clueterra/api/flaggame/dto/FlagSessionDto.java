package io.clueterra.api.flaggame.dto;

import java.util.List;

public record FlagSessionDto(FlagPlayerStatsDto stats, FlagQuestionDto current, List<String> solved) {
}
