package io.clueterra.api.game.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import io.clueterra.api.common.enums.SessionStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameResultsDto(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String level,
        SessionStatus status,
        Instant startedAt,
        long activeTimeMs,
        Instant completedAt,
        int totalXpEarned,
        long xpBefore,
        int levelBefore,
        int totalCountries,
        int guessedCountries,
        int maxAttempts,
        boolean guest,
        int streakBonusXp,
        List<ResultCountryDto> countries,
        GameResultsCommunityStatsDto communityStats) {

    public GameResultsDto withGuestAndCommunityStats(boolean guest, GameResultsCommunityStatsDto stats) {
        return new GameResultsDto(
                id, level, status, startedAt, activeTimeMs, completedAt, totalXpEarned,
                xpBefore, levelBefore, totalCountries, guessedCountries, maxAttempts,
                guest, streakBonusXp, countries, stats);
    }
}
