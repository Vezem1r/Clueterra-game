package io.clueterra.questions.clients.brands;

import io.clueterra.questions.clients.common.FetchProgressListener;
import io.clueterra.questions.clients.common.dto.FetchResult;
import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.clients.brands.dto.BrandRow;
import io.clueterra.questions.clients.brands.repository.BrandRepository;
import io.clueterra.questions.clients.common.AbstractDynamicClient;
import io.clueterra.questions.config.SourcesProperties;
import io.clueterra.questions.entity.enums.DataSource;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrandClient extends AbstractDynamicClient {

    private final SourcesProperties sourcesProperties;
    private final BrandDataLoader dataLoader;
    private final BrandRepository repository;
    private final BrandEnricher enricher;

    @PostConstruct
    public void init() {
        this.props = sourcesProperties.getSource(getSource());
        log.info("=== Brand Client Initialization ===");
        log.info("[{}] Enabled: {}", getSource(), props.isEnabled());
        log.info("===================================");
    }

    @Override
    public DataSource getSource() {
        return DataSource.GLOBAL_BRANDS;
    }

    @Override
    public String getBaseUrl() {
        return "local://global-brands.yml";
    }

    @Override
    public boolean isAvailable() {
        return props != null && props.isEnabled();
    }

    @Override
    public List<RawCountryData> fetchAllCountries(FetchProgressListener progressListener) {
        if (!isAvailable()) return List.of();
        List<BrandRow> brands = dataLoader.loadBrands();
        if (brands.isEmpty()) {
            lastFetchResult.set(new FetchResult(0, List.of()));
            return List.of();
        }
        repository.markAllAsNotFetched();
        repository.upsertBatch(brands);
        lastFetchResult.set(new FetchResult(brands.size(), List.of()));
        List<RawCountryData> result = buildResult(enricher.enrich());
        log.info("[BRANDS] fetchAllCountries completed: {} countries", result.size());
        return result;
    }

    @Override
    public Optional<RawCountryData> fetchCountryByCode(String countryCode) {
        if (!isAvailable() || !StringUtils.hasText(countryCode)) return Optional.empty();
        String iso2 = countryCode.toUpperCase();
        return buildResultOne(iso2, enricher.enrichCountry(iso2));
    }
}
