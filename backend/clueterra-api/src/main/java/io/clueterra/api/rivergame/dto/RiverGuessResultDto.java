package io.clueterra.api.rivergame.dto;

public record RiverGuessResultDto(boolean correct, int attempts, int xpAwarded, RiverRevealDto reveal,
        RiverPlayerStatsDto stats) {
}
