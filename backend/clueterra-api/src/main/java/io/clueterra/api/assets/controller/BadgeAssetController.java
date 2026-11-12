package io.clueterra.api.assets.controller;

import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.api.assets.service.BadgeAssetPayload;
import io.clueterra.api.assets.service.BadgeAssetService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/assets/badges")
@RequiredArgsConstructor
@Validated
@Tag(name = "Assets", description = "Proxy endpoints for public static assets")
public class BadgeAssetController {

    private final BadgeAssetService badgeAssetService;

    @GetMapping("/{badgeId}/{version}.png")
    @Operation(summary = "Get a versioned, optimized badge image")
    public ResponseEntity<byte[]> getBadge(
            @PathVariable @Positive long badgeId,
            @PathVariable @Pattern(regexp = "[0-9a-f]{16}") String version) {
        BadgeAssetPayload payload = badgeAssetService.fetch(badgeId, version);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header("Cross-Origin-Resource-Policy", "same-origin")
                .body(payload.bytes());
    }
}
