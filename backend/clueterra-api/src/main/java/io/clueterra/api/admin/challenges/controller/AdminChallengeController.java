package io.clueterra.api.admin.challenges.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.api.admin.challenges.dto.ChallengeAdminDto;
import io.clueterra.api.admin.challenges.dto.ChallengePageResponse;
import io.clueterra.api.admin.challenges.dto.UpdateChallengeRequest;
import io.clueterra.api.admin.challenges.service.ChallengeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/challenges")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Challenges", description = "Challenge management")
public class AdminChallengeController {

    private final ChallengeService challengeService;

    @GetMapping
    public ResponseEntity<ChallengePageResponse> listChallenges(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "challengeDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(challengeService.listChallenges(page, size, startDate, endDate, level, difficulty, active, sortBy, sortDir));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChallengeAdminDto> getChallenge(@PathVariable Long id) {
        return challengeService.getChallenge(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<ChallengeAdminDto> toggleActive(@PathVariable Long id, @RequestParam boolean active) {
        return challengeService.toggleActive(id, active).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ChallengeAdminDto> updateChallenge(@PathVariable Long id, @Valid @RequestBody UpdateChallengeRequest request) {
        return challengeService.updateChallenge(id, request.challengeLevel(), request.difficulty()).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/levels")
    public ResponseEntity<List<String>> getLevels() {
        return ResponseEntity.ok(challengeService.getLevels());
    }

    @GetMapping("/difficulties")
    public ResponseEntity<List<String>> getDifficulties() {
        return ResponseEntity.ok(challengeService.getDifficulties());
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(challengeService.getStats());
    }

    @PostMapping("/cache/invalidate")
    public ResponseEntity<Map<String, Object>> invalidateCache() {
        long evicted = challengeService.invalidateCache();
        return ResponseEntity.ok(Map.of("evicted", evicted, "status", "ok"));
    }

    @PostMapping("/cache/today/invalidate")
    @Operation(summary = "Invalidate today's challenge cache entries")
    public ResponseEntity<Map<String, Object>> invalidateTodayCache() {
        long evicted = challengeService.invalidateTodayGameplayCache();
        return ResponseEntity.ok(Map.of("evicted", evicted, "status", "ok"));
    }
}
