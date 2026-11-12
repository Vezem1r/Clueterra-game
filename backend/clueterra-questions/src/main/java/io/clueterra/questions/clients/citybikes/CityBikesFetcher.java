package io.clueterra.questions.clients.citybikes;

import io.clueterra.questions.clients.citybikes.dto.CityBikesNetworkDetail;
import io.clueterra.questions.clients.citybikes.dto.CityBikesNetworkIndexEntry;
import io.clueterra.questions.clients.citybikes.dto.CityBikesNetworkSummary;
import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.repository.CountryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CityBikesFetcher {

    private final HttpClientHelper httpClient;
    private final CityBikesParser parser;
    private final CountryRepository countryRepository;

    public List<CityBikesNetworkSummary> fetchNetworkIndex(String baseUrl, Duration timeout) {
        try {
            String url = baseUrl + "/networks?fields=id,name,href,company,location";
            return httpClient.getOptional(DataSource.CITYBIKES, url, timeout)
                    .map(parser::parseNetworkIndex)
                    .stream()
                    .flatMap(List::stream)
                    .map(this::toNetworkSummary)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (Exception e) {
            log.debug("[{}] Network index fetch failed: {}", DataSource.CITYBIKES, e.getMessage());
            return List.of();
        }
    }

    public Optional<CityBikesNetworkDetail> fetchNetworkDetail(
            String baseUrl, Duration timeout, CityBikesNetworkSummary summary) {
        try {
            String url = baseUrl + "/networks/" + summary.id() + "?fields=id,name,location,company,stations";
            return httpClient.getOptional(DataSource.CITYBIKES, url, timeout)
                    .flatMap(body -> parser.parseNetworkDetail(body, summary));
        } catch (Exception e) {
            log.debug("[{}] Network detail failed for {}: {}",
                    DataSource.CITYBIKES, summary.id(), e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<CityBikesNetworkSummary> toNetworkSummary(CityBikesNetworkIndexEntry entry) {
        Optional<Country> country = resolveCountry(entry.countryCode());
        if (country.isEmpty() || !StringUtils.hasText(country.get().getIsoAlpha2())) {
            return Optional.empty();
        }

        return Optional.of(new CityBikesNetworkSummary(
                entry.id(),
                entry.name(),
                entry.city(),
                country.get().getIsoAlpha2().toUpperCase(Locale.ROOT),
                entry.href(),
                entry.company()
        ));
    }

    private Optional<Country> resolveCountry(String code) {
        if (!StringUtils.hasText(code)) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() == 2) {
            return countryRepository.findByIsoAlpha2(normalized);
        }
        if (normalized.length() == 3) {
            return countryRepository.findByIsoAlpha3(normalized);
        }
        return Optional.empty();
    }
}
