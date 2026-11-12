package io.clueterra.api.assets.controller;

import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.api.assets.service.QuestionImageAssetPayload;
import io.clueterra.api.assets.service.QuestionImageAssetService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
@Tag(name = "Assets", description = "Proxy endpoints for public static assets")
public class QuestionImageAssetController {

    private final QuestionImageAssetService questionImageAssetService;

    @GetMapping("/hints/{hintId}")
    @Operation(summary = "Get a hint image from managed storage with automatic restore fallback")
    public ResponseEntity<byte[]> getHint(
            @PathVariable Long hintId,
            @RequestParam(name = "width", required = false) Integer width) {
        return response(questionImageAssetService.fetchHint(hintId, width));
    }

    @GetMapping("/statistics/{statisticId}")
    @Operation(summary = "Get a statistic image from managed storage with automatic restore fallback")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> getStatistic(@PathVariable Long statisticId) {
        return response(questionImageAssetService.fetchStatistic(statisticId), CacheControl.maxAge(7, TimeUnit.DAYS).cachePrivate());
    }

    private ResponseEntity<byte[]> response(QuestionImageAssetPayload payload) {
        return response(payload, CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic());
    }

    private ResponseEntity<byte[]> response(QuestionImageAssetPayload payload, CacheControl cacheControl) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .cacheControl(cacheControl)
                .body(payload.bytes());
    }
}
