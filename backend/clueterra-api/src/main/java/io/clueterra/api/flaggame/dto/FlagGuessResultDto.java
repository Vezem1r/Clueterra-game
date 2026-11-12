package io.clueterra.api.flaggame.dto;

import java.util.List;

public record FlagGuessResultDto(boolean correct, int attempts, int xpAwarded, FlagRevealDto reveal,
        List<String> misses, FlagPlayerStatsDto stats) {
}
