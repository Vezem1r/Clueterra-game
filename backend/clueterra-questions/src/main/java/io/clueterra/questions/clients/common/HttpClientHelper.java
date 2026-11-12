package io.clueterra.questions.clients.common;

import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.service.console.ConsoleLogWriter;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Component
public class HttpClientHelper {

    public static final String USER_AGENT = "Clueterra/1.0 (support@clueterra.com)";

    private static final int MAX_RETRIES = 4;
    private static final long BASE_BACKOFF_MS = 2_000;
    private static final long MAX_BACKOFF_MS = 30_000;
    private static final long MAX_SERVER_BACKOFF_SECONDS = 600;
    private static final Set<Integer> RETRYABLE_STATUSES = Set.of(429, 500, 502, 503, 504);
    private static final Pattern SECRET_QUERY_PARAM = Pattern.compile(
            "(?i)([?&](?:access_token|api_key|apikey|key|token|username)=)[^&]*");

    private final HttpClient httpClient;
    private final ConsoleLogWriter logWriter;
    private final Map<DataSource, Bucket> rateLimiters = new ConcurrentHashMap<>();

    public HttpClientHelper(ConsoleLogWriter logWriter) {
        this.logWriter = logWriter;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void configureRateLimiter(DataSource source, int requestsPerSecond, Duration period) {
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(requestsPerSecond)
                .refillIntervally(requestsPerSecond, period)
                .build();
        rateLimiters.put(source, Bucket.builder().addLimit(bandwidth).build());
        log.info("[{}] Rate limiter configured: {} req / {} ms", source, requestsPerSecond, period.toMillis());
    }

    public void configureRateLimiter(DataSource source, int requestsPerSecond) {
        configureRateLimiter(source, requestsPerSecond, Duration.ofSeconds(1));
    }

    public Optional<String> getOptional(DataSource source, String url, Duration timeout) {
        return getOptional(source, url, timeout, Map.of());
    }

    public Optional<String> getOptional(DataSource source, String url, Duration timeout, Map<String, String> headers) {
        try {
            return Optional.of(get(source, url, timeout, headers));
        } catch (DataSourceException e) {
            if (Integer.valueOf(404).equals(e.getHttpStatus())) {
                log.debug("[{}] No data (404) at: {}", source, sanitize(url));
                return Optional.empty();
            }
            throw e;
        }
    }

    public String get(DataSource source, String url, Duration timeout) {
        return get(source, url, timeout, Map.of());
    }

    public String get(DataSource source, String url, Duration timeout, Map<String, String> headers) {
        HttpRequest request = buildRequest(url, timeout, headers);
        try {
            for (int attempt = 0; ; attempt++) {
                acquireRateLimit(source);
                long start = System.currentTimeMillis();
                HttpResponse<String> response;
                try {
                    response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (IOException e) {
                    if (attempt >= MAX_RETRIES) {
                        throw new DataSourceException(source, "Request failed: " + e.getMessage(), e);
                    }
                    pause(source, exponentialBackoff(attempt), null, attempt);
                    continue;
                }
                if (attempt < MAX_RETRIES && RETRYABLE_STATUSES.contains(response.statusCode())) {
                    pause(source, retryDelay(source, response, attempt), response.statusCode(), attempt);
                    continue;
                }
                return validateResponse(source, url, response, System.currentTimeMillis() - start);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataSourceException(source, "Request interrupted", e);
        }
    }

    public CompletableFuture<String> getAsync(DataSource source, String url, Duration timeout) {
        return sendAsyncWithRetry(source, url, buildRequest(url, timeout, Map.of()), 0);
    }

    private CompletableFuture<String> sendAsyncWithRetry(DataSource source, String url, HttpRequest request, int attempt) {
        acquireRateLimit(source);
        log.debug("[{}] Async HTTP GET: {} (attempt {})", source, sanitize(url), attempt + 1);

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, error) -> {
                    boolean retryable = attempt < MAX_RETRIES
                            && (error != null || RETRYABLE_STATUSES.contains(response.statusCode()));
                    if (retryable) {
                        long delay = error != null ? exponentialBackoff(attempt) : retryDelay(source, response, attempt);
                        log.debug("[{}] Async retry {} in {}ms for {}", source, attempt + 1, delay, sanitize(url));
                        return CompletableFuture.supplyAsync(() -> null,
                                        CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS))
                                .thenCompose(ignored -> sendAsyncWithRetry(source, url, request, attempt + 1));
                    }
                    if (error != null) {
                        return CompletableFuture.<String>failedFuture(
                                new DataSourceException(source, "Request failed: " + rootMessage(error), error));
                    }
                    if (isSuccess(response.statusCode())) {
                        return CompletableFuture.completedFuture(response.body());
                    }
                    return CompletableFuture.<String>failedFuture(new DataSourceException(
                            source, sanitize(url), response.statusCode(), "HTTP " + response.statusCode()));
                })
                .thenCompose(future -> future);
    }

    public boolean healthCheck(DataSource source, String url, Duration timeout) {
        return healthCheck(source, url, timeout, Map.of());
    }

    public boolean healthCheck(DataSource source, String url, Duration timeout, Map<String, String> headers) {
        try {
            acquireRateLimit(source);
            HttpRequest request = buildRequest(url, timeout, headers);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            boolean healthy = isSuccess(response.statusCode());
            if (healthy) {
                log.debug("[{}] Health check OK", source);
            } else {
                log.warn("[{}] Health check FAILED - HTTP {}", source, response.statusCode());
            }
            return healthy;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("[{}] Health check FAILED: {}", source, e.getMessage());
            return false;
        }
    }

    private HttpRequest buildRequest(String url, Duration timeout, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT);

        if (headers != null) {
            headers.forEach(builder::setHeader);
        }
        return builder.GET().build();
    }

    private String validateResponse(DataSource source, String url, HttpResponse<String> response, long duration) {
        int status = response.statusCode();
        String safeUrl = sanitize(url);
        if (isSuccess(status)) {
            log.trace("[{}] GET {} - {}ms - OK", source, safeUrl, duration);
            return response.body();
        }
        if (status >= 500) {
            log.debug("[{}] GET {} - {}ms - FAIL {}", source, safeUrl, duration, status);
        } else {
            String body = response.body() == null
                    ? "empty"
                    : response.body().substring(0, Math.min(response.body().length(), 300));
            log.warn("[{}] GET {} - {}ms - FAIL {} body {}", source, safeUrl, duration, status, body);
        }
        throw new DataSourceException(source, safeUrl, status, "HTTP Error");
    }

    private long retryDelay(DataSource source, HttpResponse<?> response, int attempt) {
        OptionalLong serverBackoff = extractRateLimitBackoff(response);
        if (serverBackoff.isPresent()) {
            log.info("[{}] Rate-limit header detected - server requested {}ms backoff", source, serverBackoff.getAsLong());
            return serverBackoff.getAsLong();
        }
        return exponentialBackoff(attempt);
    }

    private static long exponentialBackoff(int attempt) {
        return Math.min(MAX_BACKOFF_MS, BASE_BACKOFF_MS << attempt);
    }

    private void pause(DataSource source, long backoffMs, Integer status, int attempt) throws InterruptedException {
        String reason = status == null ? "I/O error" : "HTTP " + status;
        String message = String.format("[%s] %s - retrying in %dms (Attempt %d/%d)",
                source, reason, backoffMs, attempt + 1, MAX_RETRIES);
        logWriter.info(message);
        log.warn(message);
        Thread.sleep(backoffMs);
    }

    private static OptionalLong extractRateLimitBackoff(HttpResponse<?> response) {
        return parseSeconds(response.headers().firstValue("x-ratelimit-reset"))
                .or(() -> parseSeconds(response.headers().firstValue("Retry-After")))
                .map(seconds -> OptionalLong.of(seconds * 1000))
                .orElse(OptionalLong.empty());
    }

    private static Optional<Long> parseSeconds(Optional<String> header) {
        return header.flatMap(value -> {
            try {
                long seconds = (long) Math.ceil(Double.parseDouble(value.trim()));
                return seconds > 0 && seconds <= MAX_SERVER_BACKOFF_SECONDS ? Optional.of(seconds) : Optional.empty();
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
    }

    static String sanitize(String url) {
        return url == null ? null : SECRET_QUERY_PARAM.matcher(url).replaceAll("$1***");
    }

    private boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private void acquireRateLimit(DataSource source) {
        Bucket bucket = rateLimiters.get(source);
        if (bucket != null) {
            try {
                bucket.asBlocking().consume(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DataSourceException(source, "Rate limiting interrupted", e);
            }
        }
    }

    public String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("Base URL cannot be empty");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public String buildUrl(String baseUrl, String endpoint) {
        return endpoint.startsWith("http") ? endpoint : baseUrl + endpoint;
    }

    public String normalizeCountryCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    public void backoff(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
