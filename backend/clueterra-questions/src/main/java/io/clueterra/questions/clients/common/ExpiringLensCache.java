package io.clueterra.questions.clients.common;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

public final class ExpiringLensCache<T> {

    private final Duration ttl;
    private T value;
    private Instant loadedAt;

    public ExpiringLensCache(Duration ttl) {
        this.ttl = ttl;
    }

    public synchronized T get(Supplier<T> loader) {
        Instant now = Instant.now();
        if (value == null || now.isAfter(loadedAt.plus(ttl))) {
            value = loader.get();
            loadedAt = now;
        }
        return value;
    }

    public synchronized void invalidate() {
        value = null;
        loadedAt = null;
    }
}
