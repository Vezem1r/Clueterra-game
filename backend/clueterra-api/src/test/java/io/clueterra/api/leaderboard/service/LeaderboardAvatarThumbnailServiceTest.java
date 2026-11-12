package io.clueterra.api.leaderboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import io.clueterra.api.storage.StorageObjectPayload;
import io.clueterra.api.storage.StorageService;

class LeaderboardAvatarThumbnailServiceTest {

    private static final String AVATAR_FILE = "0c1475b3-41cb-4eec-ae28-65f466530a23.png";
    private static final String THUMBNAIL_KEY = "leaderboard/128/42/" + AVATAR_FILE + ".jpg";

    private final StorageService storageService = mock(StorageService.class);
    private final LeaderboardAvatarThumbnailService service =
            new LeaderboardAvatarThumbnailService(storageService);

    @Test
    void existingLargeAvatarBecomesSmallJpeg() throws Exception {
        BufferedImage source = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = source.createGraphics();
        try {
            graphics.setColor(Color.ORANGE);
            graphics.fillRect(0, 0, 512, 512);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream original = new ByteArrayOutputStream();
        ImageIO.write(source, "png", original);
        when(storageService.readFromAvatarBucket("42/" + AVATAR_FILE))
                .thenReturn(Optional.of(new StorageObjectPayload(original.toByteArray(), "image/png")));

        byte[] thumbnail = service.create(42, AVATAR_FILE).orElseThrow();
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(thumbnail));

        assertThat(decoded.getWidth()).isEqualTo(128);
        assertThat(decoded.getHeight()).isEqualTo(128);
        assertThat(thumbnail.length).isLessThan(original.size());
        verify(storageService).readFromAvatarBucket("42/" + AVATAR_FILE);
        verify(storageService).uploadToAvatarBucket(
                eq(THUMBNAIL_KEY), any(ByteArrayInputStream.class),
                eq((long) thumbnail.length), eq("image/jpeg"));
    }

    @Test
    void cachedThumbnailDoesNotDecodeOriginalAgain() {
        byte[] thumbnail = new byte[] {1, 2, 3};
        when(storageService.readFromAvatarBucket(THUMBNAIL_KEY))
                .thenReturn(Optional.of(new StorageObjectPayload(thumbnail, "image/jpeg")));

        assertThat(service.create(42, AVATAR_FILE)).contains(thumbnail);
        verify(storageService, never()).readFromAvatarBucket("42/" + AVATAR_FILE);
    }

    @Test
    void invalidObjectNameNeverReachesStorage() {
        assertThat(service.create(42, "../secret.png")).isEmpty();
        assertThat(service.create(0, AVATAR_FILE)).isEmpty();
        verifyNoInteractions(storageService);
    }

    @Test
    void nonRasterAvatarIsNotServedAsJpeg() {
        when(storageService.readFromAvatarBucket("42/" + AVATAR_FILE))
                .thenReturn(Optional.of(new StorageObjectPayload("<svg/>".getBytes(), "image/svg+xml")));

        assertThat(service.create(42, AVATAR_FILE)).isEmpty();
    }
}
