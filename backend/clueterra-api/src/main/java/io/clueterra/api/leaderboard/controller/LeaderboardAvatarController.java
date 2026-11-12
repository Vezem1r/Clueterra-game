package io.clueterra.api.leaderboard.controller;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.clueterra.api.exception.ResourceNotFoundException;
import io.clueterra.api.leaderboard.service.LeaderboardAvatarThumbnailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/assets/avatars")
@RequiredArgsConstructor
@Tag(name = "Leaderboard", description = "Leaderboard rankings")
public class LeaderboardAvatarController {

    private final LeaderboardAvatarThumbnailService thumbnailService;

    @GetMapping(value = "/{userId}/{sourceFile}/128.jpg", produces = MediaType.IMAGE_JPEG_VALUE)
    @Operation(summary = "Get a small, cacheable leaderboard avatar")
    public ResponseEntity<byte[]> getThumbnail(
            @PathVariable long userId,
            @PathVariable String sourceFile) {
        byte[] bytes = thumbnailService.create(userId, sourceFile)
                .orElseThrow(() -> new ResourceNotFoundException("Avatar thumbnail not found"));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .contentType(MediaType.IMAGE_JPEG)
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header("Cross-Origin-Resource-Policy", "same-origin")
                .body(bytes);
    }
}
