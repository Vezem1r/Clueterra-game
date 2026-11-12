package io.clueterra.api.game.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.clueterra.api.game.repository.SessionCountryRow;
import io.clueterra.api.game.repository.SessionRow;

@Service
public class SessionActivityService {

    private final Clock clock;
    private final long maxIdleGapMs;

    public SessionActivityService(Clock clock,
                                  @Value("${clueterra.game.activity.max-idle-gap-seconds:180}") long maxIdleGapSeconds) {
        this.clock = clock;
        this.maxIdleGapMs = Math.max(0, maxIdleGapSeconds) * 1000L;
    }

    public ActivitySnapshot capture(SessionRow session, SessionCountryRow country) {
        Instant occurredAt = Instant.now(clock);
        Instant previousActivity = session.lastActivityAt() != null ? session.lastActivityAt() : session.startedAt();

        long rawGapMs = previousActivity != null
                ? Math.max(0, Duration.between(previousActivity, occurredAt).toMillis())
                : 0;
        long activeGapMs = Math.min(rawGapMs, maxIdleGapMs);
        long updatedCountryTimeMs = country.timeSpentMs() + activeGapMs;

        return new ActivitySnapshot(occurredAt, updatedCountryTimeMs);
    }

    public record ActivitySnapshot(Instant occurredAt, long updatedCountryTimeMs) {
    }
}
