package io.clueterra.api.admin.badges.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import io.clueterra.api.assets.service.BadgeAssetPayload;
import io.clueterra.api.assets.service.BadgeImageProcessor;
import io.clueterra.api.admin.badges.repository.AdminBadgeJdbcRepository;
import io.clueterra.api.storage.StorageService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BadgeImageService {

    private final AdminBadgeJdbcRepository badgeRepository;
    private final StorageService storageService;
    private final BadgeImageProcessor imageProcessor;

    public String upload(Long badgeId, MultipartFile file) {
        String oldUrl = badgeRepository.findImageUrl(badgeId);
        BadgeAssetPayload image = imageProcessor.process(file);
        String key = "badges/" + badgeId + "/" + UUID.randomUUID() + ".png";
        String url;

        try (ByteArrayInputStream input = new ByteArrayInputStream(image.bytes())) {
            url = storageService.uploadToAvatarBucket(
                    key, input, image.bytes().length, image.contentType());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to upload badge image", exception);
        }

        try {
            badgeRepository.updateImageUrl(badgeId, url);
        } catch (RuntimeException exception) {
            storageService.delete(url);
            throw exception;
        }
        if (oldUrl != null && !oldUrl.equals(url)) {
            storageService.delete(oldUrl);
        }
        return url;
    }

    public void delete(Long badgeId) {
        String url = badgeRepository.findImageUrl(badgeId);
        if (url != null) {
            badgeRepository.updateImageUrl(badgeId, null);
            storageService.delete(url);
        }
    }
}
