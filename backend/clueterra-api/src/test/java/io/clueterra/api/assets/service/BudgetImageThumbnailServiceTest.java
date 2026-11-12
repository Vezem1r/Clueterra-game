package io.clueterra.api.assets.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class BudgetImageThumbnailServiceTest {

    private final BudgetImageThumbnailService service = new BudgetImageThumbnailService();

    @Test
    void hasWebpDecoderForManagedBudgetImages() {
        assertThat(ImageIO.getImageReadersByFormatName("webp").hasNext()).isTrue();
    }

    @Test
    void appliesExifOrientationBeforeCreatingThumbnail() throws IOException {
        BufferedImage source = new BufferedImage(1600, 800, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = source.createGraphics();
        try {
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, 800, 800);
            graphics.setColor(Color.RED);
            graphics.fillRect(800, 0, 800, 800);
        } finally {
            graphics.dispose();
        }
        byte[] jpeg = jpegWithExifOrientation(source, 6);

        BudgetAssetPayload result = service.create(() -> new BudgetAssetPayload(jpeg, "image/jpeg"));
        BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(result.bytes()));

        assertThat(service.readExifOrientation(jpeg)).isEqualTo(6);
        assertThat(thumbnail.getWidth()).isEqualTo(640);
        assertThat(thumbnail.getHeight()).isEqualTo(1280);
        assertColorCloseTo(new Color(thumbnail.getRGB(320, 160)), Color.BLUE, 20);
        assertColorCloseTo(new Color(thumbnail.getRGB(320, 1120)), Color.RED, 20);
        assertThat(result.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void appliesMirroredExifOrientation() {
        BufferedImage source = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
        source.setRGB(0, 0, Color.RED.getRGB());
        source.setRGB(1, 0, Color.BLUE.getRGB());

        BufferedImage oriented = service.applyOrientation(source, 2);

        assertThat(oriented.getRGB(0, 0)).isEqualTo(Color.BLUE.getRGB());
        assertThat(oriented.getRGB(1, 0)).isEqualTo(Color.RED.getRGB());
    }

    @Test
    void preservesAspectRatioForRequestedResponsiveDimension() throws IOException {
        BufferedImage source = new BufferedImage(2400, 1600, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        ImageIO.write(source, "jpeg", encoded);

        BudgetAssetPayload result = service.createWidthVariant(
                () -> new BudgetAssetPayload(encoded.toByteArray(), "image/jpeg"),
                480);
        BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(result.bytes()));

        assertThat(thumbnail.getWidth()).isEqualTo(480);
        assertThat(thumbnail.getHeight()).isEqualTo(320);
    }

    @Test
    void ignoresMissingOrInvalidExifMetadata() {
        BufferedImage source = new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB);

        assertThat(service.applyExifOrientation(new byte[] {1, 2, 3}, source)).isSameAs(source);
    }

    private byte[] jpegWithExifOrientation(BufferedImage image, int orientation) throws IOException {
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", jpeg);
        byte[] encoded = jpeg.toByteArray();
        byte[] exif = exifOrientationBytes(orientation);
        ByteArrayOutputStream result = new ByteArrayOutputStream(encoded.length + exif.length);
        result.write(encoded, 0, 2);
        result.write(exif);
        result.write(encoded, 2, encoded.length - 2);
        return result.toByteArray();
    }

    private byte[] exifOrientationBytes(int orientation) {
        return new byte[] {
            (byte) 0xff, (byte) 0xe1, 0x00, 0x22,
            'E', 'x', 'i', 'f', 0x00, 0x00,
            'I', 'I', 0x2a, 0x00, 0x08, 0x00, 0x00, 0x00,
            0x01, 0x00,
            0x12, 0x01, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00,
            (byte) orientation, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00
        };
    }

    private void assertColorCloseTo(Color actual, Color expected, int tolerance) {
        assertThat(actual.getRed()).isBetween(expected.getRed() - tolerance, expected.getRed() + tolerance);
        assertThat(actual.getGreen())
                .isBetween(expected.getGreen() - tolerance, expected.getGreen() + tolerance);
        assertThat(actual.getBlue()).isBetween(expected.getBlue() - tolerance, expected.getBlue() + tolerance);
    }
}
