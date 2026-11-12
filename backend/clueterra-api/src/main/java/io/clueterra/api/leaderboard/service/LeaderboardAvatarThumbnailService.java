package io.clueterra.api.leaderboard.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import io.clueterra.api.storage.StorageObjectPayload;
import io.clueterra.api.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaderboardAvatarThumbnailService {

    private static final int THUMBNAIL_SIZE = 128;
    private static final Pattern AVATAR_FILE = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(?:png|jpe?g)$");

    private final StorageService storageService;

    public Optional<byte[]> create(long userId, String sourceFile) {
        if (userId <= 0 || sourceFile == null || !AVATAR_FILE.matcher(sourceFile).matches()) {
            return Optional.empty();
        }

        String thumbnailKey = "leaderboard/128/" + userId + "/" + sourceFile + ".jpg";
        Optional<StorageObjectPayload> cached = storageService.readFromAvatarBucket(thumbnailKey);
        if (cached.isPresent() && "image/jpeg".equals(cached.orElseThrow().contentType())) {
            return cached.map(StorageObjectPayload::bytes);
        }

        Optional<byte[]> thumbnail = storageService.readFromAvatarBucket(userId + "/" + sourceFile)
                .filter(payload -> "image/png".equals(payload.contentType())
                        || "image/jpeg".equals(payload.contentType()))
                .flatMap(this::resize);
        thumbnail.ifPresent(bytes -> cache(thumbnailKey, bytes));
        return thumbnail;
    }

    private void cache(String objectKey, byte[] bytes) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            storageService.uploadToAvatarBucket(objectKey, input, bytes.length, "image/jpeg");
        } catch (RuntimeException exception) {
            log.warn("Failed to cache leaderboard avatar {}: {}", objectKey, exception.getMessage());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to close thumbnail stream", exception);
        }
    }

    private Optional<byte[]> resize(StorageObjectPayload payload) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(payload.bytes());
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.setUseCache(false);
            BufferedImage source = ImageIO.read(input);
            if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0
                    || source.getWidth() > 2048 || source.getHeight() > 2048) {
                return Optional.empty();
            }

            BufferedImage thumbnail = new BufferedImage(
                    THUMBNAIL_SIZE, THUMBNAIL_SIZE, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = thumbnail.createGraphics();
            try {
                graphics.setColor(new Color(16, 24, 32));
                graphics.fillRect(0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.drawImage(source, 0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE, null);
            } finally {
                graphics.dispose();
            }

            if (!ImageIO.write(thumbnail, "jpeg", output)) {
                return Optional.empty();
            }
            return Optional.of(output.toByteArray());
        } catch (IOException exception) {
            return Optional.empty();
        }
    }
}
