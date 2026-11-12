package io.clueterra.api.admin.settings.controller;

import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.api.admin.settings.dto.SettingsAuditEntryDto;
import io.clueterra.api.admin.settings.service.SettingsService;
import io.clueterra.api.common.dto.PageResponse;
import io.clueterra.api.security.UserPrincipal;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
@Validated
@Tag(name = "Admin Settings", description = "Application settings management")
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping("/registration")
    public ResponseEntity<Map<String, Boolean>> getRegistrationStatus() {
        return ResponseEntity.ok(Map.of("enabled", settingsService.isRegistrationEnabled()));
    }

    @PutMapping("/registration")
    public ResponseEntity<Map<String, Boolean>> setRegistrationStatus(
            @RequestBody Map<String, Boolean> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        settingsService.setRegistrationEnabled(enabled, principal);
        return ResponseEntity.ok(Map.of("enabled", enabled));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllSettings() {
        return ResponseEntity.ok(settingsService.getAllSettings());
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateSettings(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(settingsService.updateSettings(body, principal));
    }

    @GetMapping("/audit")
    public ResponseEntity<PageResponse<SettingsAuditEntryDto>> getRecentAudit(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "8") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(settingsService.getRecentAudit(page, size));
    }
}
