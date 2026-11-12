package io.clueterra.api.admin.countries.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.clueterra.api.admin.countries.dto.CountryAdminDto;
import io.clueterra.api.admin.countries.dto.UpdateCountryRequest;
import io.clueterra.api.admin.countries.service.CountryService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/countries")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Countries", description = "Country management")
public class AdminCountryController {

    private final CountryService countryService;

    @GetMapping
    public ResponseEntity<Page<CountryAdminDto>> listCountries(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "nameEn") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(countryService.listCountries(page, size, region, search, active, sortBy, sortDir));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CountryAdminDto> getCountry(@PathVariable Long id) {
        return countryService.getCountry(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<Map<String, Object>> getCountryStats(@PathVariable Long id) {
        return countryService.getCountryStats(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<CountryAdminDto> updateCountry(@PathVariable Long id, @Valid @RequestBody UpdateCountryRequest request) {
        return countryService.updateCountry(id, request).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<CountryAdminDto> toggleActive(@PathVariable Long id, @RequestParam boolean active) {
        return countryService.toggleActive(id, active).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/regions")
    public ResponseEntity<List<String>> getRegions() {
        return ResponseEntity.ok(countryService.getRegions());
    }
}
