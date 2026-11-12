package io.clueterra.api.admin.countries.service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import io.clueterra.api.admin.countries.dto.CountryAdminDto;
import io.clueterra.api.admin.countries.dto.UpdateCountryRequest;
import io.clueterra.api.admin.countries.repository.AdminCountryJdbcRepository;
import io.clueterra.api.common.utils.database.SortDirection;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CountryService {

    private final AdminCountryJdbcRepository countryRepo;
    private final CountryCountsCacheService countsCacheService;

    public Page<CountryAdminDto> listCountries(int page, int size, String region, String search, Boolean active, String sortBy, String sortDir) {
        boolean isComputedSort = "hintCount".equals(sortBy) || "statisticCount".equals(sortBy);
        if (isComputedSort) {
            return getManualPagedCountries(page, size, region, search, active, sortBy, sortDir);
        }

        Pageable pageable = PageRequest.of(page, size);
        return countryRepo.findAll(region, search, active, sortBy, sortDir, pageable).map(this::enrichWithCounts);
    }

    public Optional<CountryAdminDto> getCountry(Long id) {
        return countryRepo.findById(id).map(this::enrichWithCounts);
    }

    public Optional<Map<String, Object>> getCountryStats(Long id) {
        return countryRepo.findById(id).map(dto -> {
            var enriched = enrichWithCounts(dto);
            long totalHints = countryRepo.countHintsByCountryId(id);
            long activeHints = countryRepo.countActiveHintsByCountryId(id);
            return Map.of("country", enriched, "totalHints", totalHints, "activeHints", activeHints);
        });
    }

    public Optional<CountryAdminDto> updateCountry(Long id, UpdateCountryRequest request) {
        log.info("Updating country ID {}: {}", id, request);
        return countryRepo.update(id, request.nameEn(), request.nameNative(), request.capital(), request.difficultyModifier() != null ? BigDecimal.valueOf(request.difficultyModifier()) : null, request.isActive()).map(this::enrichWithCounts);
    }

    public Optional<CountryAdminDto> toggleActive(Long id, boolean active) {
        log.info("Toggling active status for country ID {} to {}", id, active);
        return countryRepo.setActive(id, active).map(this::enrichWithCounts);
    }

    public List<String> getRegions() {
        return countryRepo.findAllRegions();
    }

    private CountryAdminDto enrichWithCounts(CountryAdminDto dto) {
        return dto.withCounts(countsCacheService.getHintCount(dto.id()), countsCacheService.getStatisticCount(dto.id()));
    }

    private Page<CountryAdminDto> getManualPagedCountries(int page, int size, String region, String search, Boolean active, String sortBy, String sortDir) {
        List<CountryAdminDto> all = countryRepo.findAll(region, search, active, "nameEn", SortDirection.ASC, Pageable.unpaged()).getContent().stream().map(this::enrichWithCounts).collect(Collectors.toList());

        Comparator<CountryAdminDto> cmp;
        if ("hintCount".equals(sortBy)) {
            cmp = Comparator.comparingLong(d -> Objects.requireNonNullElse(d.hintCount(), 0L));
        } else {
            cmp = Comparator.comparingLong(d -> Objects.requireNonNullElse(d.statisticCount(), 0L));
        }

        if (sortDir.equalsIgnoreCase("desc")) cmp = cmp.reversed();
        all.sort(cmp);

        int start = page * size;
        int end = Math.min(start + size, all.size());
        List<CountryAdminDto> pageContent = start < all.size() ? all.subList(start, end) : List.of();
        return new PageImpl<>(pageContent, PageRequest.of(page, size), all.size());
    }
}
