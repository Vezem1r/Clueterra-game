package io.clueterra.api.game.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.clueterra.api.common.enums.SessionStatus;

public record SessionRow(
        long id,
        Long userId,
        String guestToken,
        long challengeId,
        LocalDate challengeDate,
        String challengeLevel,
        SessionStatus status,
        int currentCountryIdx,
        Instant startedAt,
        Instant lastActivityAt,
        Instant completedAt,
        int totalXpEarned,
        int maxHintsPerCountry,
        int maxAttempts,
        long xpBefore,
        int levelBefore,
        String termsVersionAccepted,
        String privacyVersionAccepted,
        String hintsJson,
        List<SessionCountryRow> countries) {
}
