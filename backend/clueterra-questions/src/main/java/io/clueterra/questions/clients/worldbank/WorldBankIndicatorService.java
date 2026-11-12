package io.clueterra.questions.clients.worldbank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.entity.enums.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorldBankIndicatorService {

    private final HttpClientHelper httpClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CompletableFuture<WorldBankCountryIndicators> fetchIndicatorsForCountryAsync(
            String baseUrl, String countryCode, int yearRange, Duration timeout,
            Map<String, IndicatorConfig> indicators) {

        log.debug("[WorldBank] Fetching {} indicators for {}", indicators.size(), countryCode);
        int currentYear = Year.now(clock).getValue();

        List<IndicatorFetch> fetches = indicators.entrySet().stream()
                .map(entry -> new IndicatorFetch(entry.getKey(),
                        fetchIndicatorAsync(baseUrl, countryCode, entry.getKey(), entry.getValue(),
                                currentYear - yearRange, currentYear, timeout)))
                .toList();

        return CompletableFuture.allOf(fetches.stream()
                        .map(IndicatorFetch::future)
                        .toArray(CompletableFuture[]::new))
                .handle((ignored, ex) -> collect(countryCode, fetches, indicators.size()));
    }

    private WorldBankCountryIndicators collect(String countryCode, List<IndicatorFetch> fetches, int total) {
        List<RawStatistic> statistics = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (IndicatorFetch fetch : fetches) {
            if (fetch.future().isCompletedExceptionally()) {
                failed.add(fetch.indicatorCode());
                continue;
            }
            fetch.future().join().ifPresent(statistics::add);
        }

        if (!failed.isEmpty()) {
            log.warn("[WorldBank] {} failed to fetch {}/{} indicators: {}",
                    countryCode, failed.size(), total, failed);
        }
        log.info("[WorldBank] {} fetched {}/{} indicators with data", countryCode, statistics.size(), total);
        return new WorldBankCountryIndicators(statistics, failed);
    }

    private CompletableFuture<Optional<RawStatistic>> fetchIndicatorAsync(
            String baseUrl,
            String countryCode,
            String indicatorCode,
            IndicatorConfig config,
            int fromYear,
            int toYear,
            Duration timeout) {

        String endpoint = String.format("/country/%s/indicator/%s?format=json&date=%d:%d&per_page=20",
                countryCode, indicatorCode, fromYear, toYear);
        String url = httpClient.buildUrl(baseUrl, endpoint);

        return httpClient.getAsync(DataSource.WORLD_BANK, url, timeout)
                .thenApply(response -> parseWorldBankArray(response).stream()
                        .filter(item -> item.hasNonNull("value"))
                        .findFirst()
                        .map(item -> new RawStatistic(
                                indicatorCode,
                                config.name(),
                                config.category(),
                                BigDecimal.valueOf(item.get("value").asDouble()),
                                null,
                                config.unit(),
                                parseYear(item.get("date")),
                                BigDecimal.ONE,
                                config.weight())));
    }

    public List<JsonNode> parseWorldBankArray(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (!root.isArray() || root.size() < 2) return List.of();

            JsonNode dataArray = root.get(1);
            if (dataArray == null || dataArray.isNull() || !dataArray.isArray()) return List.of();

            List<JsonNode> result = new ArrayList<>();
            for (JsonNode item : dataArray) result.add(item);

            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse World Bank response", e);
        }
    }

    private Integer parseYear(JsonNode dateNode) {
        if (dateNode == null || dateNode.isNull()) return null;
        String date = dateNode.asText();
        if (date.length() < 4) return null;
        try {
            return Integer.parseInt(date.substring(0, 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
