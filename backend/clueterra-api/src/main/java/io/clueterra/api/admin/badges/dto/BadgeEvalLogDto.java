package io.clueterra.api.admin.badges.dto;

import java.time.Instant;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record BadgeEvalLogDto(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long badgeId,
        String evaluationType,
        int usersEvaluated,
        int usersGranted,
        Instant startedAt,
        Instant completedAt,
        Long durationMs,
        String errorMessage) {
}
