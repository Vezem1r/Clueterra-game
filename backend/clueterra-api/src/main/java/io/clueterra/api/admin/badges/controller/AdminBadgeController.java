package io.clueterra.api.admin.badges.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.clueterra.api.admin.badges.dto.BadgeAdminDto;
import io.clueterra.api.admin.badges.dto.BadgeEvalLogDto;
import io.clueterra.api.admin.badges.dto.ReorderRequest;
import io.clueterra.api.admin.badges.dto.SaveBadgeRequest;
import io.clueterra.api.admin.badges.service.AdminBadgeService;
import io.clueterra.api.admin.badges.service.BadgeImageService;
import io.clueterra.api.common.enums.BadgeColorPreset;
import io.clueterra.api.common.enums.BadgeGrantType;
import io.clueterra.api.common.enums.BadgeTrigger;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/badges")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Badges", description = "Badge management")
public class AdminBadgeController {

    private final AdminBadgeService badgeService;
    private final BadgeImageService imageService;

    @GetMapping
    public ResponseEntity<Page<BadgeAdminDto>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(1000) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String grantType,
            @RequestParam(required = false) Boolean isSecret,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "created_at") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(badgeService.findAll(search, grantType, isSecret, isActive, sortBy, sortDir, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BadgeAdminDto> get(@PathVariable Long id) {
        return badgeService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<BadgeAdminDto> create(@Valid @RequestBody SaveBadgeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(badgeService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BadgeAdminDto> update(@PathVariable Long id, @RequestBody SaveBadgeRequest request) {
        return badgeService.update(id, request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        imageService.delete(id);
        if (badgeService.delete(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/image")
    public ResponseEntity<Map<String, String>> uploadImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        String url = imageService.upload(id, file);
        return ResponseEntity.ok(Map.of("imageUrl", url));
    }

    @DeleteMapping("/{id}/image")
    public ResponseEntity<Void> deleteImage(@PathVariable Long id) {
        imageService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/reorder")
    public ResponseEntity<Void> reorder(@Valid @RequestBody ReorderRequest request) {
        List<Long> ids = request.orderedIds().stream().map(Long::parseLong).toList();
        badgeService.reorderBadges(ids);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/evaluate")
    public ResponseEntity<Map<String, String>> evaluateAll() {
        badgeService.evaluateAllCustomBadges();
        return ResponseEntity.ok(Map.of("status", "completed"));
    }

    @PostMapping("/{id}/evaluate")
    public ResponseEntity<Map<String, String>> evaluateSingle(@PathVariable Long id) {
        badgeService.evaluateSingleBadge(id);
        return ResponseEntity.ok(Map.of("status", "completed"));
    }

    @GetMapping("/metadata")
    public ResponseEntity<Map<String, Object>> metadata() {
        List<String> grantTypes = Arrays.stream(BadgeGrantType.values()).map(Enum::name).toList();
        List<String> triggers = Arrays.stream(BadgeTrigger.values()).map(Enum::name).toList();
        List<String> filterFields = List.of(
                "challenges_played", "challenges_won", "current_streak", "best_streak",
                "total_xp", "current_level", "win_rate", "account_age_days"
        );
        return ResponseEntity.ok(Map.of(
                "grantTypes", grantTypes,
                "triggers", triggers,
                "filterFields", filterFields,
                "badgeColorKeys", BadgeColorPreset.keys()
        ));
    }

    @GetMapping("/eval-log")
    public ResponseEntity<Page<BadgeEvalLogDto>> evalLog(
            @RequestParam(required = false) Long badgeId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(badgeService.getEvalLog(badgeId, page, size));
    }
}
