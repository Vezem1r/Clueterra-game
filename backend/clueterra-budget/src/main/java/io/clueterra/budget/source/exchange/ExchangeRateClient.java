package io.clueterra.budget.source.exchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.budget.config.ExchangeRateProperties;
import io.clueterra.budget.config.ExternalHttpProperties;
import io.clueterra.budget.config.RetryableRequestTag;
import io.clueterra.budget.exception.ExternalSourceException;

import lombok.RequiredArgsConstructor;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Component
@RequiredArgsConstructor
public class ExchangeRateClient {

    private static final String EXPECTED_HOST = "api.frankfurter.dev";
    private static final String ECB_PROVIDER = "ECB";
    private static final String BUNDESBANK_HISTORICAL_PROVIDER = "BBK";

    private final OkHttpClient externalSourceHttpClient;
    private final ObjectMapper objectMapper;
    private final ExchangeRateProperties properties;
    private final ExternalHttpProperties httpProperties;
    private final Clock clock = Clock.systemUTC();

    private static final int LATEST_SNAPSHOT_KEY = 0;

    private final Map<Integer, ExchangeRateSnapshot> snapshots = new ConcurrentHashMap<>();

    public ExchangeRateSnapshot usdRatesForYear(Integer year) {
        int key = year == null ? LATEST_SNAPSHOT_KEY : year;
        ExchangeRateSnapshot current = snapshots.get(key);
        if (isFresh(current)) {
            return current;
        }
        synchronized (this) {
            current = snapshots.get(key);
            if (isFresh(current)) {
                return current;
            }
            ExchangeRateSnapshot fetched = fetchUsdRates(year);
            snapshots.put(key, fetched);
            return fetched;
        }
    }

    private ExchangeRateSnapshot fetchUsdRates(Integer year) {
        if (!properties.enabled()) {
            return usdOnlySnapshot(year);
        }
        HttpUrl endpoint = trustedEndpoint();
        HttpUrl.Builder url =
                endpoint
                        .newBuilder()
                        .addPathSegment("v2")
                        .addPathSegment("rates")
                        .addQueryParameter("base", "USD")
                        .addQueryParameter(
                                "providers",
                                year != null && year < 1999 ? BUNDESBANK_HISTORICAL_PROVIDER : ECB_PROVIDER);
        ExchangeRateBasis basis = ExchangeRateBasis.LATEST_REFERENCE_RATE;
        if (year != null) {
            LocalDate today = LocalDate.now(clock);
            LocalDate periodEnd = LocalDate.of(year, 12, 31);
            if (periodEnd.isAfter(today)) {
                periodEnd = today;
            }
            if (year <= today.getYear()) {
                url.addQueryParameter("from", LocalDate.of(year, 1, 1).toString())
                        .addQueryParameter("to", periodEnd.toString())
                        .addQueryParameter("group", "month");
                basis = ExchangeRateBasis.ANNUAL_AVERAGE;
            }
        }
        Request request =
                new Request.Builder()
                        .url(url.build())
                        .header("Accept", "application/json")
                        .header("User-Agent", httpProperties.userAgent())
                        .tag(RetryableRequestTag.class, RetryableRequestTag.READ_ONLY)
                        .get()
                        .build();
        try (Response response = externalSourceHttpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new ExternalSourceException(
                        "Exchange-rate request failed with HTTP " + response.code());
            }
            JsonNode payload = objectMapper.readTree(body.string());
            if (!payload.isArray()) {
                throw new ExternalSourceException("Exchange-rate response is not an array");
            }
            Map<String, BigDecimal> totals = new LinkedHashMap<>();
            Map<String, Integer> counts = new HashMap<>();
            Map<String, LocalDate> latestDates = new HashMap<>();
            payload.forEach(
                    rate -> {
                        String currency = rate.path("quote").asText("").toUpperCase(Locale.ROOT);
                        String rateDate = rate.path("date").asText("");
                        if (currency.matches("^[A-Z]{3}$")
                                && rate.path("rate").isNumber()
                                && !rateDate.isBlank()) {
                            totals.merge(currency, rate.path("rate").decimalValue(), BigDecimal::add);
                            counts.merge(currency, 1, Integer::sum);
                            latestDates.merge(
                                    currency,
                                    LocalDate.parse(rateDate),
                                    (first, second) -> second.isAfter(first) ? second : first);
                        }
                    });
            if (totals.isEmpty()) {
                throw new ExternalSourceException("Exchange-rate response contains no currency rates");
            }
            ExchangeRateBasis quoteBasis = basis;
            Map<String, ExchangeRateQuote> quotes = new LinkedHashMap<>();
            totals.forEach(
                    (currency, total) ->
                            quotes.put(
                                    currency,
                                    new ExchangeRateQuote(
                                            total.divide(
                                                    BigDecimal.valueOf(counts.get(currency)), 10, RoundingMode.HALF_UP),
                                            latestDates.get(currency),
                                            quoteBasis)));
            LocalDate latestRateDate =
                    quotes.values().stream()
                            .map(ExchangeRateQuote::rateDate)
                            .max(LocalDate::compareTo)
                            .orElse(LocalDate.now(clock));
            quotes.put("USD", new ExchangeRateQuote(BigDecimal.ONE, latestRateDate, basis));
            return new ExchangeRateSnapshot(Instant.now(clock), Map.copyOf(quotes));
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ExternalSourceException externalSourceException) {
                throw externalSourceException;
            }
            throw new ExternalSourceException("Exchange-rate request failed", exception);
        }
    }

    private ExchangeRateSnapshot usdOnlySnapshot(Integer year) {
        LocalDate rateDate =
                year == null
                        ? LocalDate.now(clock)
                        : LocalDate.of(year, 12, 31).isAfter(LocalDate.now(clock))
                          ? LocalDate.now(clock)
                          : LocalDate.of(year, 12, 31);
        return new ExchangeRateSnapshot(
                Instant.now(clock),
                Map.of(
                        "USD",
                        new ExchangeRateQuote(
                                BigDecimal.ONE,
                                rateDate,
                                year == null
                                        ? ExchangeRateBasis.LATEST_REFERENCE_RATE
                                        : ExchangeRateBasis.ANNUAL_AVERAGE)));
    }

    private boolean isFresh(ExchangeRateSnapshot snapshot) {
        return snapshot != null
                && snapshot.fetchedAt().plus(properties.cacheDuration()).isAfter(Instant.now(clock));
    }

    private HttpUrl trustedEndpoint() {
        HttpUrl endpoint = HttpUrl.parse(properties.apiUrl());
        if (endpoint == null
                || !endpoint.isHttps()
                || !EXPECTED_HOST.equalsIgnoreCase(endpoint.host())) {
            throw new ExternalSourceException("Invalid exchange-rate API endpoint");
        }
        return endpoint;
    }
}
