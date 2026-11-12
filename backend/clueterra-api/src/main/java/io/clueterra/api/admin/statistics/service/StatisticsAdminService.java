package io.clueterra.api.admin.statistics.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.clueterra.api.admin.statistics.dto.CountryStatisticAdminDto;
import io.clueterra.api.admin.statistics.dto.StatCountryOption;
import io.clueterra.api.admin.statistics.dto.UpdateStatisticRequest;
import io.clueterra.api.admin.statistics.repository.AdminStatisticsJdbcRepository;
import io.clueterra.api.storage.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class StatisticsAdminService {

    private final AdminStatisticsJdbcRepository repo;
    private final StorageService storageService;

    public Page<CountryStatisticAdminDto> listStatistics(int page, int size, String search,
                                                         Long countryId, String category,
                                                         String source, String indicatorCode,
                                                         Boolean isCurrent, Integer year,
                                                         String sortBy, String sortDir) {
        Pageable pageable = PageRequest.of(page, size);
        return repo.findAll(search, countryId, category, source, indicatorCode, isCurrent, year,
                sortBy, sortDir, pageable).map(this::resolveImageUrl);
    }

    public Optional<CountryStatisticAdminDto> getStatistic(Long id) {
        return repo.findById(id).map(this::resolveImageUrl);
    }

    @Transactional
    public Optional<CountryStatisticAdminDto> updateStatistic(Long id, UpdateStatisticRequest request) {
        log.info("Updating statistic ID {}: {}", id, request);
        if (Boolean.FALSE.equals(request.isCurrent())) {
            repo.createSuppression(id);
        } else if (Boolean.TRUE.equals(request.isCurrent())) {
            repo.resolveSuppression(id);
        }
        return repo.update(id, request).map(this::resolveImageUrl);
    }

    public List<String> getCategories() {
        return repo.findDistinctCategories();
    }

    public List<String> getSources() {
        return repo.findDistinctSources();
    }

    public List<String> getIndicatorsByCategory(String category) {
        return repo.findDistinctIndicatorsByCategory(category);
    }

    public List<Integer> getYears() {
        return repo.findDistinctYears();
    }

    public List<StatCountryOption> getCountriesForFilter(String search) {
        return repo.findCountriesForFilter(search);
    }

    private CountryStatisticAdminDto resolveImageUrl(CountryStatisticAdminDto statistic) {
        return statistic.withImageUrl(
                storageService.resolveStatisticAssetUrl(statistic.id(), statistic.imageUrl()));
    }
}
