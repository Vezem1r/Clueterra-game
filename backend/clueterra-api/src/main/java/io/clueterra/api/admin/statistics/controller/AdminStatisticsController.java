package io.clueterra.api.admin.statistics.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.clueterra.api.admin.statistics.dto.CountryStatisticAdminDto;
import io.clueterra.api.admin.statistics.dto.StatCountryOption;
import io.clueterra.api.admin.statistics.dto.UpdateStatisticRequest;
import io.clueterra.api.admin.statistics.service.StatisticsAdminService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/statistics")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Statistics", description = "Country statistics management")
public class AdminStatisticsController {

    private final StatisticsAdminService service;

    @GetMapping
    public ResponseEntity<Page<CountryStatisticAdminDto>> listStatistics(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long countryId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String indicatorCode,
            @RequestParam(required = false) Boolean isCurrent,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "fetchedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(service.listStatistics(page, size, search, countryId, category, source, indicatorCode, isCurrent, year, sortBy, sortDir));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CountryStatisticAdminDto> getStatistic(@PathVariable Long id) {
        return service.getStatistic(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<CountryStatisticAdminDto> updateStatistic(@PathVariable Long id, @RequestBody UpdateStatisticRequest request) {
        return service.updateStatistic(id, request).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(service.getCategories());
    }

    @GetMapping("/sources")
    public ResponseEntity<List<String>> getSources() {
        return ResponseEntity.ok(service.getSources());
    }

    @GetMapping("/categories/{category}/indicators")
    public ResponseEntity<List<String>> getIndicatorsByCategory(@PathVariable String category) {
        return ResponseEntity.ok(service.getIndicatorsByCategory(category));
    }

    @GetMapping("/years")
    public ResponseEntity<List<Integer>> getYears() {
        return ResponseEntity.ok(service.getYears());
    }

    @GetMapping("/countries")
    public ResponseEntity<List<StatCountryOption>> getCountriesForFilter(@RequestParam(required = false) String search) {
        return ResponseEntity.ok(service.getCountriesForFilter(search));
    }
}
