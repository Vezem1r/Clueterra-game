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

class BadgeImageProcessorTest {

    private final BadgeImageProcessor processor = new BadgeImageProcessor();

    @Test
    void preservesWidePngAspectRatioAndTransparency() throws IOException {
        BadgeAssetPayload result = processor.process(png(800, 400));
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(result.bytes()));

        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(image.getWidth()).isEqualTo(384);
        assertThat(image.getHeight()).isEqualTo(192);
        assertThat(image.getColorModel().hasAlpha()).isTrue();
        assertThat((image.getRGB(0, 0) >>> 24) & 0xff).isZero();
    }

    @Test
    void preservesPortraitPngAspectRatio() throws IOException {
        BadgeAssetPayload result = processor.process(png(400, 800));
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(result.bytes()));

        assertThat(image.getWidth()).isEqualTo(192);
        assertThat(image.getHeight()).isEqualTo(384);
    }

    private byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(20, 140, 240, 220));
            graphics.fillOval(width / 4, height / 4, width / 2, height / 2);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}
