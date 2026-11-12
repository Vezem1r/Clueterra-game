package io.clueterra.budget.config;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
public class RetryingGetInterceptor implements Interceptor {

    private static final Set<Integer> RETRYABLE_STATUS_CODES =
            Set.of(408, 425, 429, 500, 502, 503, 504);

    private final int maxAttempts;
    private final Duration retryBaseDelay;
    private final Duration retryMaxDelay;

    public RetryingGetInterceptor(int maxAttempts, Duration retryBaseDelay, Duration retryMaxDelay) {
        this.maxAttempts = maxAttempts;
        this.retryBaseDelay = retryBaseDelay;
        this.retryMaxDelay = retryMaxDelay;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        return execute(chain, request, 1, isRetryable(request));
    }

    private Response execute(Chain chain, Request request, int attempt, boolean retryable)
            throws IOException {
        Response response;
        try {
            response = chain.proceed(request);
        } catch (IOException exception) {
            if (!canRetry(attempt, retryable)) {
                throw exception;
            }
            Duration delay = exponentialDelay(attempt);
            logRetry(request, attempt, "I/O failure", delay);
            pause(delay);
            return execute(chain, request, attempt + 1, retryable);
        }

        if (!canRetry(attempt, retryable) || !RETRYABLE_STATUS_CODES.contains(response.code())) {
            return response;
        }

        Optional<Duration> retryDelay = retryDelay(response, attempt);
        if (retryDelay.isEmpty()) {
            log.warn(
                    "External source {} returned HTTP {} with Retry-After above {}; not retrying",
                    request.url().host(),
                    response.code(),
                    retryMaxDelay);
            return response;
        }
        Duration delay = retryDelay.orElseThrow();
        logRetry(request, attempt, "HTTP " + response.code(), delay);
        response.close();
        pause(delay);
        return execute(chain, request, attempt + 1, retryable);
    }

    private boolean isRetryable(Request request) {
        return "GET".equalsIgnoreCase(request.method())
                || "HEAD".equalsIgnoreCase(request.method())
                || request.tag(RetryableRequestTag.class) == RetryableRequestTag.READ_ONLY;
    }

    private boolean canRetry(int attempt, boolean retryable) {
        return attempt < maxAttempts && retryable;
    }

    private Optional<Duration> retryDelay(Response response, int attempt) {
        String retryAfter = response.header("Retry-After");
        if (retryAfter == null || retryAfter.isBlank()) {
            return Optional.of(exponentialDelay(attempt));
        }
        try {
            return boundedServerDelay(Duration.ofSeconds(Long.parseLong(retryAfter.trim())));
        } catch (NumberFormatException exception) {
            try {
                Duration serverDelay =
                        Duration.between(
                                ZonedDateTime.now(),
                                ZonedDateTime.parse(retryAfter.trim(), DateTimeFormatter.RFC_1123_DATE_TIME));
                return boundedServerDelay(serverDelay);
            } catch (DateTimeParseException ignored) {
                return Optional.of(exponentialDelay(attempt));
            }
        }
    }

    private Duration exponentialDelay(int attempt) {
        long multiplier = 1L << Math.min(attempt - 1, 10);
        Duration upperBound = clamp(retryBaseDelay.multipliedBy(multiplier));
        long upperMillis = upperBound.toMillis();
        if (upperMillis <= 1) {
            return upperBound;
        }
        long lowerMillis = Math.max(1, upperMillis / 2);
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(lowerMillis, upperMillis + 1));
    }

    private Optional<Duration> boundedServerDelay(Duration delay) {
        Duration normalized = delay.isNegative() ? Duration.ZERO : delay;
        return normalized.compareTo(retryMaxDelay) <= 0 ? Optional.of(normalized) : Optional.empty();
    }

    private Duration clamp(Duration delay) {
        return delay.compareTo(retryMaxDelay) > 0 ? retryMaxDelay : delay;
    }

    private void pause(Duration delay) throws InterruptedIOException {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            InterruptedIOException interrupted =
                    new InterruptedIOException("External source retry was interrupted");
            interrupted.initCause(exception);
            throw interrupted;
        }
    }

    private void logRetry(Request request, int attempt, String reason, Duration delay) {
        log.warn(
                "External source {} failed on attempt {}/{} with {}; retrying in {} ms",
                request.url().host(),
                attempt,
                maxAttempts,
                reason,
                delay.toMillis());
    }
}
