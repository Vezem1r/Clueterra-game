package io.clueterra.questions.clients.common;

import io.clueterra.questions.clients.common.dto.FetchResult;
import io.clueterra.questions.clients.common.dto.SourceProperties;
import io.clueterra.questions.config.SourcesProperties;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractSourceClient implements DataSourceClient {

    protected final AtomicReference<FetchResult> lastFetchResult = new AtomicReference<>();

    protected SourceProperties props;
    protected String normalizedBaseUrl;
    protected Duration timeout;
    protected String apiKey;

    protected void initBase(HttpClientHelper httpClient, SourcesProperties sourcesProperties) {
        this.props = sourcesProperties.getSource(getSource());
        if (isDisabled()) {
            return;
        }
        this.normalizedBaseUrl = httpClient.normalizeBaseUrl(props.getBaseUrl());
        this.timeout = Duration.ofSeconds(props.getTimeoutSeconds());
        if (props.getRateLimitPerMinute() != null) {
            httpClient.configureRateLimiter(
                    getSource(), props.getRateLimitPerMinute(), Duration.ofMinutes(1));
        } else {
            httpClient.configureRateLimiter(getSource(), props.getRateLimitPerSecond());
        }
    }

    protected boolean isDisabled() {
        return props == null || !props.isEnabled();
    }

    @Override
    public String getBaseUrl() {
        return StringUtils.defaultString(normalizedBaseUrl);
    }

    @Override
    public FetchResult getLastFetchResult() {
        return lastFetchResult.get();
    }
}
