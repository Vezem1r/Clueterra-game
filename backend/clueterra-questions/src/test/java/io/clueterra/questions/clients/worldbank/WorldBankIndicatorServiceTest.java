package io.clueterra.questions.clients.worldbank;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.clients.common.DataSourceException;
import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorldBankIndicatorServiceTest {

    private final HttpClientHelper httpClient = mock(HttpClientHelper.class);
    private final WorldBankIndicatorService service = new WorldBankIndicatorService(
            httpClient, new ObjectMapper(), Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void failedRequestIsReportedAsFailureNotAsMissingData() {
        Map<String, IndicatorConfig> indicators = new LinkedHashMap<>();
        indicators.put("SP.POP.TOTL", new IndicatorConfig("Population", Category.DEMOGRAPHICS, "people", BigDecimal.ONE));
        indicators.put("NY.GDP.MKTP.CD", new IndicatorConfig("GDP", Category.ECONOMY, "USD", BigDecimal.ONE));
        indicators.put("EN.POP.DNST", new IndicatorConfig("Density", Category.DEMOGRAPHICS, "people/km2", BigDecimal.ONE));

        when(httpClient.buildUrl(eq("https://wb"), any())).thenAnswer(invocation -> "https://wb" + invocation.getArgument(1));
        when(httpClient.getAsync(eq(DataSource.WORLD_BANK), contains("SP.POP.TOTL"), any(Duration.class)))
                .thenReturn(CompletableFuture.completedFuture("[{\"page\":1},[{\"value\":83000000,\"date\":\"2025\"}]]"));
        when(httpClient.getAsync(eq(DataSource.WORLD_BANK), contains("NY.GDP.MKTP.CD"), any(Duration.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        new DataSourceException(DataSource.WORLD_BANK, "https://wb", 503, "HTTP 503")));
        when(httpClient.getAsync(eq(DataSource.WORLD_BANK), contains("EN.POP.DNST"), any(Duration.class)))
                .thenReturn(CompletableFuture.completedFuture("[{\"page\":1},[{\"value\":null,\"date\":\"2025\"}]]"));

        WorldBankCountryIndicators result = service
                .fetchIndicatorsForCountryAsync("https://wb", "DE", 10, Duration.ofSeconds(5), indicators)
                .join();

        assertThat(result.statistics()).extracting(stat -> stat.indicatorCode()).containsExactly("SP.POP.TOTL");
        assertThat(result.failedIndicators()).containsExactly("NY.GDP.MKTP.CD");
        assertThat(result.complete()).isFalse();
    }
}
