package io.clueterra.api.admin.challenges.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record ChallengeAdminDto(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        LocalDate challengeDate,
        String challengeLevel,
        String difficulty,
        Integer totalCountries,
        Integer totalHints,
        Boolean isActive,
        LocalDateTime createdAt,
        boolean canRerollCountry,
        boolean canRerollHint,
        @JsonInclude(JsonInclude.Include.NON_NULL) String countriesJson,
        @JsonInclude(JsonInclude.Include.NON_NULL) String hintsJson) {

    public ChallengeAdminDto withRerollState(boolean canRerollCountry, boolean canRerollHint, String hintsJson) {
        return new ChallengeAdminDto(
                id,
                challengeDate,
                challengeLevel,
                difficulty,
                totalCountries,
                totalHints,
                isActive,
                createdAt,
                canRerollCountry,
                canRerollHint,
                countriesJson,
                hintsJson);
    }
}
