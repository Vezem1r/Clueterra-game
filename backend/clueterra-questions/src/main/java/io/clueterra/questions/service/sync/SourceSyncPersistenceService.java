package io.clueterra.questions.service.sync;

import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.repository.jdbc.CountryJdbcRepository;
import io.clueterra.questions.service.challenge.CountryVisibilityProfileService;
import io.clueterra.questions.service.enrichment.StatisticEnrichmentService;
import io.clueterra.questions.service.enrichment.dto.IndicatorKey;
import io.clueterra.questions.service.source.SourceModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SourceSyncPersistenceService {

    private final CountryJdbcRepository countryRepository;
    private final StatisticRepository statisticRepository;
    private final CountryVisibilityProfileService visibilityProfileService;
    private final StatisticEnrichmentService enrichmentService;

    @Transactional
    public SlicePersistenceResult persistSlice(SourceModule module, RawCountryData rawData, boolean allowStaleCleanup) {
        Optional<Country> mergedOpt = module.statisticProcessor().mergeCountryData(List.of(rawData));
        if (mergedOpt.isEmpty()) {
            return SlicePersistenceResult.empty();
        }

        Country savedCountry = saveCountrySnapshot(mergedOpt.get());

        List<CountryStatistic> statistics = module.policy().dynamic()
                ? module.statisticProcessor().buildDynamicStatistics(
                savedCountry.getId(),
                module.source(),
                rawData.getFetchedAt(),
                rawData.getStatistics())
                : module.statisticProcessor().buildStatisticsSnapshot(savedCountry.getId(), List.of(rawData));

        if (!statistics.isEmpty()) {
            statisticRepository.batchUpsert(statistics);
        }

        int staleDeleted = 0;
        if (!allowStaleCleanup) {
            log.info("[{}] [{}] Incomplete fetch - keeping statistics missing from this snapshot",
                    module.source(), rawData.getPreferredIsoCode());
        } else if (statistics.isEmpty()) {
            staleDeleted = statisticRepository.deleteAllCurrentByCountryAndSource(
                    savedCountry.getId(), module.source(), module.policy().dynamic());
        } else {
            staleDeleted = statisticRepository.deleteStaleSnapshot(
                    savedCountry.getId(), module.source(), statistics, module.policy().dynamic());
        }

        Set<String> changedIndicatorCodes = statistics.stream()
                .map(CountryStatistic::getIndicatorCode)
                .collect(Collectors.toUnmodifiableSet());
        Set<IndicatorKey> comparableKeys = enrichmentService.collectComparableIndicatorKeys(statistics);
        return new SlicePersistenceResult(
                savedCountry, statistics.size(), staleDeleted, comparableKeys, changedIndicatorCodes);
    }

    private Country saveCountrySnapshot(Country incoming) {
        Optional<Country> existingOpt = Optional.ofNullable(incoming.getIsoAlpha2())
                .flatMap(countryRepository::findByIsoAlpha2)
                .or(() -> Optional.ofNullable(incoming.getIsoAlpha3()).flatMap(countryRepository::findByIsoAlpha3));

        if (existingOpt.isPresent()) {
            return countryRepository.fillMissingFromSource(existingOpt.get(), incoming);
        }

        incoming.setRegion(null);
        incoming.setSubregion(null);
        incoming.setIsActive(Boolean.TRUE);
        incoming.setDifficultyModifier(visibilityProfileService.getModifier(incoming.getIsoAlpha2()));
        return countryRepository.insert(incoming);
    }
}
