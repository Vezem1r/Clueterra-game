package io.clueterra.api.common.utils.redis;

import java.time.Duration;

public record RedisWindowCounter(long count, Duration remaining) {
}
