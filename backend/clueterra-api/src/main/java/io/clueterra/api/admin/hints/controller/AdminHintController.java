package io.clueterra.api.admin.hints.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.clueterra.api.admin.hints.dto.CreateHintRequest;
import io.clueterra.api.admin.hints.dto.HintAdminDto;
import io.clueterra.api.admin.hints.dto.HintCountryOption;
import io.clueterra.api.admin.hints.dto.ReplaceHintImageRequest;
import io.clueterra.api.admin.hints.dto.UpdateHintRequest;
import io.clueterra.api.admin.hints.service.HintService;

import org.springframework.http.HttpStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/hints")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Hints", description = "Hint management")
public class AdminHintController {

    private final HintService hintService;

    @PostMapping
    public ResponseEntity<HintAdminDto> createHint(@Valid @RequestBody CreateHintRequest request) {
        return hintService.createHint(request)
                .map(dto -> ResponseEntity.status(HttpStatus.CREATED).body(dto))
                .orElse(ResponseEntity.badRequest().build());
    }

    @GetMapping
    public ResponseEntity<Page<HintAdminDto>> listHints(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<Long> countryIds,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String hintType,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer difficultyMin,
            @RequestParam(required = false) Integer difficultyMax,
            @RequestParam(required = false) Integer sourceYear,
            @RequestParam(required = false) Boolean hasImage,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(hintService.listHints(page, size, search, countryIds, category, hintType, source, active, difficultyMin, difficultyMax, sourceYear, hasImage, sortBy, sortDir));
    }

    @GetMapping("/{id}")
    public ResponseEntity<HintAdminDto> getHint(@PathVariable Long id) {
        return hintService.getHint(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<HintAdminDto> updateHint(@PathVariable Long id, @Valid @RequestBody UpdateHintRequest request) {
        return hintService.updateHint(id, request).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<HintAdminDto> toggleActive(@PathVariable Long id, @RequestParam boolean active) {
        return hintService.toggleActive(id, active).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/image")
    @Operation(summary = "Replace an image hint image from an exact Wikimedia Commons file name")
    public ResponseEntity<HintAdminDto> replaceHintImage(
            @PathVariable Long id,
            @Valid @RequestBody ReplaceHintImageRequest request) {
        return hintService.replaceHintImage(id, request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/image")
    public ResponseEntity<HintAdminDto> deleteHintImage(@PathVariable Long id) {
        return hintService.deleteHintImage(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDeleteHint(@PathVariable Long id) {
        return hintService.softDelete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(hintService.getCategories());
    }

    @GetMapping("/types")
    public ResponseEntity<List<String>> getHintTypes() {
        return ResponseEntity.ok(hintService.getHintTypes());
    }

    @GetMapping("/sources")
    public ResponseEntity<List<String>> getSources() {
        return ResponseEntity.ok(hintService.getSources());
    }

    @GetMapping("/source-years")
    public ResponseEntity<List<Integer>> getSourceYears() {
        return ResponseEntity.ok(hintService.getSourceYears());
    }

    @GetMapping("/countries")
    public ResponseEntity<List<HintCountryOption>> getCountriesForFilter(@RequestParam(required = false) String search) {
        return ResponseEntity.ok(hintService.getCountriesForFilter(search));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getHintStats() {
        return ResponseEntity.ok(hintService.getStats());
    }

    @PostMapping("/cache/invalidate")
    public ResponseEntity<Map<String, Object>> invalidateCache() {
        long evicted = hintService.invalidateCache();
        return ResponseEntity.ok(Map.of("evicted", evicted, "status", "ok"));
    }
}
