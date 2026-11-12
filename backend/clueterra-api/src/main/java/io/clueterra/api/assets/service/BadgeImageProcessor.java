package io.clueterra.api.assets.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BadgeImageProcessor {

    private static final int MAXIMUM_DIMENSION = 384;
    private static final long MAXIMUM_SOURCE_PIXELS = 25_000_000L;
    private static final long MAXIMUM_SOURCE_BYTES = 10L * 1024 * 1024;
    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final Semaphore CONCURRENT_RESIZES = new Semaphore(2);

    public BadgeAssetPayload process(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Badge image is required");
        }
        if (file.getSize() > MAXIMUM_SOURCE_BYTES) {
            throw new IllegalArgumentException("Badge image is too large");
        }
        try {
            return process(file.getBytes());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to read badge image", exception);
        }
    }

    public BadgeAssetPayload process(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAXIMUM_SOURCE_BYTES) {
            throw new IllegalArgumentException("Invalid badge image size");
        }
        acquireResizePermit();
        try {
            BufferedImage source = decode(bytes);
            return new BadgeAssetPayload(encode(scale(source)), PNG_CONTENT_TYPE);
        } finally {
            CONCURRENT_RESIZES.release();
        }
    }

    int maximumDimension() {
        return MAXIMUM_DIMENSION;
    }

    private BufferedImage decode(byte[] bytes) {
        ImageIO.setUseCache(false);
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new IllegalArgumentException("Invalid badge image");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("Unsupported badge image format; use PNG or JPEG");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAXIMUM_SOURCE_PIXELS) {
                    throw new IllegalArgumentException("Badge image dimensions are too large");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new IllegalArgumentException("Invalid badge image");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid badge image", exception);
        }
    }

    private BufferedImage scale(BufferedImage source) {
        double factor = Math.min(
                1.0D,
                (double) MAXIMUM_DIMENSION / Math.max(source.getWidth(), source.getHeight()));
        int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * factor));
        int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * factor));
        BufferedImage current = source;
        int width = source.getWidth();
        int height = source.getHeight();

        while (width > targetWidth || height > targetHeight) {
            width = Math.max(targetWidth, width / 2);
            height = Math.max(targetHeight, height / 2);
            current = resize(current, width, height);
        }
        return current;
    }

    private BufferedImage resize(BufferedImage source, int width, int height) {
        int imageType = source.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;
        BufferedImage resized = new BufferedImage(width, height, imageType);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private byte[] encode(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IllegalStateException("PNG encoder is unavailable");
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to encode badge image", exception);
        }
    }

    private void acquireResizePermit() {
        try {
            CONCURRENT_RESIZES.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Badge image processing was interrupted", exception);
        }
    }
}
