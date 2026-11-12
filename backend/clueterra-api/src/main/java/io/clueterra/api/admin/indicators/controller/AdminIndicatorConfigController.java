package io.clueterra.api.admin.indicators.controller;

import io.clueterra.api.admin.indicators.dto.IndicatorConfigAdminDto;
import io.clueterra.api.admin.indicators.service.IndicatorConfigAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/indicators")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Indicators", description = "Indicator configuration management")
public class AdminIndicatorConfigController {

    private final IndicatorConfigAdminService service;

    @GetMapping
    public ResponseEntity<Page<IndicatorConfigAdminDto>> list(
            @RequestParam(defaultValue = "regular") String kind,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String profileLevel,
            @RequestParam(required = false) String familyType,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "category") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(service.listIndicators(
                kind, page, size, search, category, profileLevel, familyType, isActive, sortBy, sortDir
        ));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> categories(
            @RequestParam(defaultValue = "regular") String kind) {
        return ResponseEntity.ok(service.getCategories(kind));
    }

    @GetMapping("/profile-levels")
    public ResponseEntity<List<String>> profileLevels(
            @RequestParam(defaultValue = "regular") String kind) {
        return ResponseEntity.ok(service.getProfileLevels(kind));
    }

    @PostMapping("/cache/invalidate")
    public ResponseEntity<Map<String, Object>> invalidateCache() {
        long evicted = service.invalidateCache();
        return ResponseEntity.ok(Map.of("evicted", evicted, "status", "ok"));
    }
}
