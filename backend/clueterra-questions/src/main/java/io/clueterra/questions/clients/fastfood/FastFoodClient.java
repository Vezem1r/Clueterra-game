package io.clueterra.questions.clients.fastfood;

import io.clueterra.questions.clients.common.DataSourceClient;
import io.clueterra.questions.clients.common.FetchProgressListener;
import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.repository.CountryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class FastFoodClient implements DataSourceClient {

    private final FastFoodConfigLoader configLoader;
    private final FastFoodEnricher enricher;
    private final CountryRepository countryRepository;

    @Override
    public DataSource getSource() {
        return DataSource.FAST_FOOD;
    }

    @Override
    public boolean isAvailable() {
        return configLoader.getData() != null
                && configLoader.getData().countries() != null
                && !configLoader.getData().countries().isEmpty();
    }

    @Override
    public List<RawCountryData> fetchAllCountries(FetchProgressListener progressListener) {
        if (!isAvailable()) return List.of();
        List<RawCountryData> result = enricher.enrichAll(countryRepository.findByIsActiveTrue());
        log.info("[{}] fetchAllCountries completed: {} countries with data", getSource(), result.size());
        return result;
    }

    @Override
    public Optional<RawCountryData> fetchCountryByCode(String countryCode) {
        if (!isAvailable()) return Optional.empty();
        return countryRepository.findByIsoAlpha2(countryCode.toUpperCase())
                .flatMap(enricher::enrichCountry);
    }

    @Override
    public String getBaseUrl() {
        return "yaml://fastfood-regional.yml";
    }
}
