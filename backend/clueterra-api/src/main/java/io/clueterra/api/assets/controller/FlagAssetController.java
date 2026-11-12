package io.clueterra.api.assets.controller;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.api.assets.service.FlagAssetPayload;
import io.clueterra.api.assets.service.FlagAssetService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
@Validated
@Tag(name = "Assets", description = "Proxy endpoints for public static assets")
public class FlagAssetController {

    private static final String FLAG_CODES = "^[A-Za-z]{2}(,[A-Za-z]{2}){0,259}$";
    private static final String CODE_SEPARATOR = ",";
    private static final int CACHE_DAYS = 30;

    private final FlagAssetService flagAssetService;

    @GetMapping(value = "/flags/bundle", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the SVG markup of several bundled flags in one response, keyed by lower-case ISO-2 code")
    public ResponseEntity<Map<String, String>> getFlagBundle(@RequestParam @Pattern(regexp = FLAG_CODES) String codes) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(CACHE_DAYS, TimeUnit.DAYS).cachePublic().immutable())
                .body(flagAssetService.fetchFlags(Arrays.asList(codes.split(CODE_SEPARATOR))));
    }

    @GetMapping("/flags/{iso2}.svg")
    public ResponseEntity<byte[]> getFlagSvg(
            @PathVariable String iso2,
            @RequestParam(defaultValue = "w160") String size) {
        return buildFlagResponse(iso2);
    }

    @GetMapping("/flags/{iso2}.png")
    public ResponseEntity<byte[]> getLegacyFlag(
            @PathVariable String iso2,
            @RequestParam(defaultValue = "w160") String size) {
        return buildFlagResponse(iso2);
    }

    private ResponseEntity<byte[]> buildFlagResponse(String iso2) {
        FlagAssetPayload payload = flagAssetService.fetchFlag(iso2);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .cacheControl(CacheControl.maxAge(CACHE_DAYS, TimeUnit.DAYS).cachePublic().immutable())
                .body(payload.bytes());
    }
}
