package io.clueterra.api.assets.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import io.clueterra.api.assets.repository.BadgeAssetJdbcRepository;
import io.clueterra.api.storage.StorageObjectPayload;
import io.clueterra.api.storage.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BadgeAssetService {

    private static final int VERSION_LENGTH = 16;

    private final BadgeAssetJdbcRepository repository;
    private final StorageService storageService;
    private final BadgeImageProcessor imageProcessor;

    public String resolveUrl(long badgeId, String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return imageUrl;
        }
        if (imageUrl.startsWith("/api/assets/badges/")) {
            return imageUrl;
        }
        if (storageService.extractAvatarObjectKey(imageUrl) == null) {
            return storageService.resolveAvatarUrl(imageUrl);
        }
        return "/api/assets/badges/" + badgeId + "/" + version(imageUrl) + ".png";
    }

    public BadgeAssetPayload fetch(long badgeId, String requestedVersion) {
        String imageUrl = repository.findImageUrl(badgeId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Badge image not found"));
        String expectedVersion = version(imageUrl);
        if (!MessageDigest.isEqual(
                expectedVersion.getBytes(StandardCharsets.US_ASCII),
                requestedVersion.getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(NOT_FOUND, "Badge image version not found");
        }

        String sourceKey = storageService.extractAvatarObjectKey(imageUrl);
        if (sourceKey == null) {
            throw new ResponseStatusException(NOT_FOUND, "Badge image not found");
        }
        String optimizedKey = optimizedKey(badgeId, expectedVersion);
        Optional<StorageObjectPayload> cached = storageService.readFromAvatarBucket(optimizedKey);
        if (cached.isPresent()) {
            return toPayload(cached.orElseThrow());
        }

        StorageObjectPayload source = storageService.readFromAvatarBucket(sourceKey)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Badge image not found"));
        BadgeAssetPayload optimized = imageProcessor.process(source.bytes());
        cache(optimizedKey, optimized);
        return optimized;
    }

    private void cache(String objectKey, BadgeAssetPayload payload) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(payload.bytes())) {
            storageService.uploadToAvatarBucket(
                    objectKey, input, payload.bytes().length, payload.contentType());
        } catch (RuntimeException exception) {
            log.warn("Failed to cache optimized badge image {}: {}", objectKey, exception.getMessage());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to cache optimized badge image", exception);
        }
    }

    private String optimizedKey(long badgeId, String version) {
        return "badges/optimized/" + imageProcessor.maximumDimension()
                + "/" + badgeId + "/" + version + ".png";
    }

    private String version(String imageUrl) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(imageUrl.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, VERSION_LENGTH / 2);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private BadgeAssetPayload toPayload(StorageObjectPayload payload) {
        return new BadgeAssetPayload(payload.bytes(), payload.contentType());
    }
}
