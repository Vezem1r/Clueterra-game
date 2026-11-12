package io.clueterra.api.flaggame.dto;

import java.util.List;

public record FlagAnswerDto(FlagRevealDto reveal, List<String> misses, FlagPlayerStatsDto stats) {
}
