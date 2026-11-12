package io.clueterra.api.game.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import io.clueterra.api.common.enums.SessionStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameSessionDto(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String guestToken,
        boolean guest,
        String level,
        SessionStatus status,
        int currentCountryIndex,
        int totalCountries,
        int maxHintsPerCountry,
        int maxAttempts,
        Instant startedAt,
        long activeTimeMs,
        Instant completedAt,
        int totalXpEarned,
        long xpBefore,
        int levelBefore,
        List<SessionCountryDto> countries) {
}
