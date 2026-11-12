package io.clueterra.api.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import io.clueterra.api.storage.StorageProperties;

class AvatarImageServiceTest {

    private AvatarImageService service;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.setMaxFileSize(2_097_152);
        service = new AvatarImageService(properties);
    }

    @Test
    @DisplayName("process re-encodes PNG avatar to normalized square PNG")
    void processReEncodesPngAvatar() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                imageBytes());

        ProcessedAvatar avatar = service.process(file);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(avatar.data()));
        assertThat(avatar.contentType()).isEqualTo("image/png");
        assertThat(avatar.extension()).isEqualTo("png");
        assertThat(decoded.getWidth()).isEqualTo(512);
        assertThat(decoded.getHeight()).isEqualTo(512);
    }

    @Test
    @DisplayName("process rejects unsupported avatar content type")
    void processRejectsUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.gif",
                "image/gif",
                "GIF89a".getBytes());

        assertThatThrownBy(() -> service.process(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported avatar file type. Use JPEG or PNG.");
    }

    @Test
    @DisplayName("process rejects empty remote avatar payloads")
    void processRejectsEmptyRemoteAvatarPayloads() {
        assertThatThrownBy(() -> service.process(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Avatar file is required");
    }

    @Test
    @DisplayName("process rejects files whose bytes do not match the declared content type")
    void processRejectsSpoofedContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "GIF89a".getBytes());

        assertThatThrownBy(() -> service.process(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Uploaded avatar content does not match its file type");
    }

    @Test
    @DisplayName("process rejects decompression bombs before allocating their pixels")
    void processRejectsDecompressionBombBeforeDecoding() throws Exception {
        byte[] bomb = withPngHeaderDimensions(imageBytes(1, 1, "png"), 50_000, 50_000);

        assertThatThrownBy(() -> service.process(bomb))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Avatar image is too large. Max dimensions: 4096x4096");
    }

    @Test
    @DisplayName("process rejects PNG and JPEG avatars wider than the source limit")
    void processRejectsOversizedAvatarDimensions() throws Exception {
        MockMultipartFile png = new MockMultipartFile("file", "avatar.png", "image/png", imageBytes(4097, 1, "png"));
        MockMultipartFile jpeg = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", imageBytes(1, 4097, "jpg"));

        assertThatThrownBy(() -> service.process(png))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Avatar image is too large. Max dimensions: 4096x4096");
        assertThatThrownBy(() -> service.process(jpeg))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Avatar image is too large. Max dimensions: 4096x4096");
    }

    @Test
    @DisplayName("process accepts avatars exactly at the source dimension limit")
    void processAcceptsAvatarAtDimensionLimit() throws Exception {
        ProcessedAvatar avatar = service.process(imageBytes(4096, 16, "png"));

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(avatar.data()));
        assertThat(decoded.getWidth()).isEqualTo(512);
        assertThat(decoded.getHeight()).isEqualTo(512);
    }

    private byte[] imageBytes() throws Exception {
        return imageBytes(640, 320, "png");
    }

    private byte[] imageBytes(int width, int height, String format) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, output);
            return output.toByteArray();
        }
    }

    private byte[] withPngHeaderDimensions(byte[] png, int width, int height) {
        byte[] patched = png.clone();
        ByteBuffer buffer = ByteBuffer.wrap(patched);
        buffer.putInt(16, width);
        buffer.putInt(20, height);
        CRC32 crc = new CRC32();
        crc.update(patched, 12, 17);
        buffer.putInt(29, (int) crc.getValue());
        return patched;
    }
}
