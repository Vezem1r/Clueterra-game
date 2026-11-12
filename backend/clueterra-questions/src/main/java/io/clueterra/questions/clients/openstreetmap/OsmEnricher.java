package io.clueterra.questions.clients.openstreetmap;

import io.clueterra.questions.clients.openstreetmap.dao.OsmCountryStatisticDao;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.repository.jdbc.CountryJdbcRepository;
import io.clueterra.questions.service.enrichment.StatisticEnrichmentService;
import io.clueterra.questions.service.enrichment.dto.IndicatorKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OsmEnricher {

    private final OsmCountryStatisticDao dao;
    private final StatisticRepository statisticRepository;
    private final CountryJdbcRepository countryRepository;
    private final StatisticEnrichmentService enrichmentService;

    public int enrich() {
        dao.syncAllToMain();

        Set<IndicatorKey> osmKeys = statisticRepository.findBySource(DataSource.OPEN_STREET_MAP).stream()
                .filter(s -> s.getIsCurrent() != null && s.getIsCurrent())
                .map(s -> new IndicatorKey(s.getSource(), s.getIndicatorCode(), s.getYear()))
                .collect(Collectors.toSet());

        int enriched = enrichmentService.enrichIndicators(osmKeys);
        log.info("[OSM] Sync + enrichment completed: {} indicators, {} statistics", osmKeys.size(), enriched);
        return enriched;
    }

    public int enrichCountry(String countryCode) {
        dao.syncCountryToMain(countryCode);

        Set<IndicatorKey> osmKeys = countryRepository.findByIsoAlpha2(countryCode)
                .or(() -> countryRepository.findByIsoAlpha3(countryCode))
                .map(country -> statisticRepository.findCurrentByCountryIdAndSource(country.getId(), DataSource.OPEN_STREET_MAP))
                .orElseGet(List::of)
                .stream()
                .filter(s -> s.getIsCurrent() != null && s.getIsCurrent())
                .map(CountryStatistic::getIndicatorCode)
                .distinct()
                .map(code -> new IndicatorKey(DataSource.OPEN_STREET_MAP, code, null))
                .collect(Collectors.toSet());

        int enriched = enrichmentService.enrichIndicators(osmKeys);
        log.info("[OSM] Country {} sync + targeted enrichment completed: {} indicators, {} statistics",
                countryCode, osmKeys.size(), enriched);
        return enriched;
    }
}
