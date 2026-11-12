package io.clueterra.questions.storage;

import java.time.Clock;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ImageStorageServiceCompressionTest {

    @Test
    void strictStorageCheckDoesNotMisclassifyInfrastructureFailureAsMissingObject() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        doThrow(new RuntimeException("DNS lookup failed"))
                .when(minioClient)
                .statObject(any(StatObjectArgs.class));
        ImageStorageService service = new ImageStorageService(minioClient, properties(), Clock.systemUTC());

        assertThatThrownBy(() -> service.objectExistsStrict("hint-images", "ai/us/example.jpg"))
                .isInstanceOf(MediaStorageAccessException.class)
                .hasMessageContaining("hint-images/ai/us/example.jpg")
                .hasRootCauseMessage("DNS lookup failed");
    }

    @Test
    void recompressesOpaqueRasterToJpegAndResizesIt() throws Exception {
        ImageStorageService service = new ImageStorageService(mock(MinioClient.class), properties(), Clock.systemUTC());
        byte[] body = pngBytes(false);

        PreparedImageObject prepared = service.prepareForStorage(
                "ai/test-image",
                "https://example.com/test.png",
                "image/png",
                body
        );

        assertThat(prepared.objectPath()).isEqualTo("ai/test-image.jpg");
        assertThat(prepared.contentType()).isEqualTo("image/jpeg");

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(prepared.bytes()));
        assertThat(decoded.getWidth()).isEqualTo(100);
        assertThat(decoded.getHeight()).isEqualTo(50);
        assertThat(decoded.getColorModel().hasAlpha()).isFalse();
    }

    @Test
    void keepsExplicitPngForTransparentRasterImages() throws Exception {
        ImageStorageService service = new ImageStorageService(mock(MinioClient.class), properties(), Clock.systemUTC());
        byte[] body = pngBytes(true);

        PreparedImageObject prepared = service.prepareForStorage(
                "flags/us.png",
                "https://example.com/us.png",
                "image/png",
                body
        );

        assertThat(prepared.objectPath()).isEqualTo("flags/us.png");
        assertThat(prepared.contentType()).isEqualTo("image/png");

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(prepared.bytes()));
        assertThat(decoded.getWidth()).isEqualTo(100);
        assertThat(decoded.getHeight()).isEqualTo(50);
        assertThat(decoded.getColorModel().hasAlpha()).isTrue();
    }

    @Test
    void passesThroughSvgWithoutCompression() {
        ImageStorageService service = new ImageStorageService(mock(MinioClient.class), properties(), Clock.systemUTC());
        byte[] body = """
                <svg xmlns="http://www.w3.org/2000/svg" width="64" height="32">
                  <rect width="64" height="32" fill="#fff"/>
                </svg>
                """.getBytes(StandardCharsets.UTF_8);

        PreparedImageObject prepared = service.prepareForStorage(
                "flags/test-flag",
                "https://example.com/test-flag.svg",
                "image/svg+xml",
                body
        );

        assertThat(prepared.objectPath()).isEqualTo("flags/test-flag.svg");
        assertThat(prepared.contentType()).isEqualTo("image/svg+xml");
        assertThat(prepared.bytes()).isEqualTo(body);
    }

    @Test
    void extractObjectPathAcceptsPublicMinioUrls() {
        ImageStorageService service = new ImageStorageService(mock(MinioClient.class), properties(), Clock.systemUTC());

        String objectPath = service.extractObjectPath("https://cdn.example.com/storage/hint-images/ai/zw/candidate.jpg");

        assertThat(objectPath).isEqualTo("ai/zw/candidate.jpg");
    }

    @Test
    void extractObjectPathRejectsApplicationAssetRoutes() {
        ImageStorageService service = new ImageStorageService(mock(MinioClient.class), properties(), Clock.systemUTC());

        assertThat(service.extractObjectPath("/api/assets/flags/cz.svg")).isNull();
    }

    @Test
    void stripsTrackingQueryFromWikimediaDownloadUrlsOnly() {
        URI wikimedia = ImageStorageService.normalizeDownloadUri(
                "https://upload.wikimedia.org/wikipedia/commons/a/a1/Example.jpg?utm_source=commons#preview");
        URI signedExternal = ImageStorageService.normalizeDownloadUri(
                "https://images.example.com/example.jpg?token=secret");

        assertThat(wikimedia.toString())
                .isEqualTo("https://upload.wikimedia.org/wikipedia/commons/a/a1/Example.jpg");
        assertThat(signedExternal.toString())
                .isEqualTo("https://images.example.com/example.jpg?token=secret");
    }

    @Test
    void parsesRetryAfterAsSecondsOrHttpDate() {
        Instant now = Instant.parse("2026-09-15T10:00:00Z");

        assertThat(ImageStorageService.parseRetryAfter("17", now)).isEqualTo(Duration.ofSeconds(17));
        assertThat(ImageStorageService.parseRetryAfter("Tue, 15 Sep 2026 10:00:42 GMT", now))
                .isEqualTo(Duration.ofSeconds(42));
        assertThat(ImageStorageService.parseRetryAfter("invalid", now)).isZero();
    }

    @Test
    void storesBundledImageBytesAtTheExactObjectPath() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        doThrow(new RuntimeException("object missing"))
                .when(minioClient)
                .statObject(any(StatObjectArgs.class));
        ImageStorageService service = new ImageStorageService(minioClient, properties(), Clock.systemUTC());
        byte[] body = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8);

        assertThat(service.storeBytes(body, "natural-earth/it.svg", "image/svg+xml"))
                .contains("natural-earth/it.svg");
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void doesNotUploadBundledImageWhenTheDeterministicObjectAlreadyExists() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        ImageStorageService service = new ImageStorageService(minioClient, properties(), Clock.systemUTC());
        byte[] body = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8);

        assertThat(service.storeBytes(body, "natural-earth/it.svg", "image/svg+xml"))
                .contains("natural-earth/it.svg");
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    private static ImageStorageProperties properties() {
        ImageStorageProperties properties = new ImageStorageProperties();
        properties.setEndpoint("http://localhost:9000");
        properties.setPublicEndpoint("https://cdn.example.com/storage");
        properties.setCompressRasterImages(true);
        properties.setMaxWidth(100);
        properties.setMaxHeight(100);
        properties.setJpegQuality(0.8f);
        return properties;
    }

    private static byte[] pngBytes(boolean transparent) throws Exception {
        BufferedImage image = new BufferedImage(
                200,
                100,
                transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = image.createGraphics();
        try {
            if (transparent) {
                graphics.setComposite(AlphaComposite.Clear);
                graphics.fillRect(0, 0, 200, 100);
                graphics.setComposite(AlphaComposite.SrcOver);
            } else {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, 200, 100);
            }
            graphics.setColor(new Color(18, 99, 201, transparent ? 180 : 255));
            graphics.fillRect(10, 10, 200 - 20, 100 - 20);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
