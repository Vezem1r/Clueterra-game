package io.clueterra.api.legal.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.api.legal.dto.LegalAcceptanceRequest;
import io.clueterra.api.legal.dto.LegalCurrentResponse;
import io.clueterra.api.legal.service.LegalAcceptanceService;
import io.clueterra.api.legal.dto.PublicDataSourcesResponse;
import io.clueterra.api.legal.service.PublicDataSourcesService;
import io.clueterra.api.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/legal")
@RequiredArgsConstructor
@Tag(name = "Legal", description = "Current legal versions and acceptance endpoints")
public class LegalController {

    private final LegalAcceptanceService legalAcceptanceService;
    private final PublicDataSourcesService publicDataSourcesService;

    @GetMapping("/current")
    @Operation(summary = "Get current Terms and Privacy versions")
    public ResponseEntity<LegalCurrentResponse> getCurrent() {
        return ResponseEntity.ok(legalAcceptanceService.getCurrent());
    }

    @GetMapping("/data-sources")
    @Operation(summary = "Get public data source notices and attribution links")
    public ResponseEntity<PublicDataSourcesResponse> getPublicDataSources() {
        return ResponseEntity.ok(publicDataSourcesService.getPublicDataSources());
    }

    @PostMapping("/accept")
    @Operation(summary = "Accept the current Terms and Privacy versions")
    public ResponseEntity<Void> acceptCurrent(
            @Valid @RequestBody LegalAcceptanceRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("Authentication is required for legal acceptance");
        }

        String source = principal.isGuest()
                ? LegalAcceptanceService.SOURCE_GUEST_REACCEPT
                : LegalAcceptanceService.SOURCE_USER_REACCEPT;
        legalAcceptanceService.recordUserAcceptance(
                principal.getId(), request.termsVersion(), request.privacyVersion(), source);
        return ResponseEntity.noContent().build();
    }
}
