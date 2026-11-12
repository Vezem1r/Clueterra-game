package io.clueterra.api.assets.controller;

import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.api.assets.service.BudgetAssetPayload;
import io.clueterra.api.assets.service.BudgetAssetService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/assets/budget")
@RequiredArgsConstructor
@Tag(name = "Assets", description = "Proxy endpoints for public static assets")
public class BudgetAssetController {

    private final BudgetAssetService budgetAssetService;

    @GetMapping("/variants/{variantId}/{role}")
    @Operation(summary = "Get an image used by today's BudgetGuessr question")
    public ResponseEntity<byte[]> getVariantImage(
            @PathVariable long variantId,
            @PathVariable String role,
            @RequestParam(required = false) Integer width) {
        return response(budgetAssetService.fetchVariantImage(variantId, role, width));
    }

    @GetMapping("/admin/variants/{variantId}/{role}")
    @Operation(summary = "Get an image used by a generated BudgetGuessr question")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> getAdminVariantImage(
            @PathVariable long variantId,
            @PathVariable String role,
            @RequestParam(defaultValue = "false") boolean thumbnail) {
        return privateResponse(
                budgetAssetService.fetchAdminVariantImage(variantId, role, thumbnail));
    }

    @GetMapping("/candidates/{wikidataId}/{category}/{sourceSha1}")
    @Operation(summary = "Get a versioned BudgetGuessr candidate image")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> getCandidateImage(
            @PathVariable String wikidataId,
            @PathVariable String category,
            @PathVariable String sourceSha1,
            @RequestParam(defaultValue = "false") boolean thumbnail) {
        return privateResponse(
                budgetAssetService.fetchCandidateImage(
                        wikidataId, category, sourceSha1, thumbnail));
    }

    @GetMapping("/projects/{wikidataId}/{sourceSha1}")
    @Operation(summary = "Get the image for a published BudgetGuessr project page")
    public ResponseEntity<byte[]> getPublishedProjectImage(
            @PathVariable String wikidataId,
            @PathVariable String sourceSha1,
            @RequestParam(required = false) Integer width) {
        return response(budgetAssetService.fetchPublishedProjectImage(wikidataId, sourceSha1, width));
    }

    private ResponseEntity<byte[]> response(BudgetAssetPayload payload) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header("Cross-Origin-Resource-Policy", "same-origin")
                .body(payload.bytes());
    }

    private ResponseEntity<byte[]> privateResponse(BudgetAssetPayload payload) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePrivate().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header("Cross-Origin-Resource-Policy", "same-origin")
                .body(payload.bytes());
    }
}
