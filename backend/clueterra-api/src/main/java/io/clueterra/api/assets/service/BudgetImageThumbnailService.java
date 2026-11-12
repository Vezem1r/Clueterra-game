package io.clueterra.api.assets.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.stereotype.Service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;

@Service
public class BudgetImageThumbnailService {

    private static final int MAXIMUM_DIMENSION = 1280;
    private static final float JPEG_QUALITY = 0.92F;
    private static final String JPEG_CONTENT_TYPE = "image/jpeg";
    private static final Semaphore CONCURRENT_RESIZES = new Semaphore(2);

    public BudgetAssetPayload create(Supplier<BudgetAssetPayload> sourceSupplier) {
        return resize(sourceSupplier, MAXIMUM_DIMENSION, false);
    }

    public BudgetAssetPayload createWidthVariant(
            Supplier<BudgetAssetPayload> sourceSupplier, int targetWidth) {
        return resize(sourceSupplier, targetWidth, true);
    }

    private BudgetAssetPayload resize(
            Supplier<BudgetAssetPayload> sourceSupplier, int targetSize, boolean widthOnly) {
        if (targetSize <= 0) {
            throw new IllegalArgumentException("Target image size must be positive");
        }
        acquireResizePermit();
        try {
            BudgetAssetPayload source = sourceSupplier.get();
            BufferedImage decoded = decode(source.bytes());
            BufferedImage oriented = applyExifOrientation(source.bytes(), decoded);
            return new BudgetAssetPayload(
                    encode(scale(oriented, targetSize, widthOnly)), JPEG_CONTENT_TYPE);
        } finally {
            CONCURRENT_RESIZES.release();
        }
    }

    int maximumDimension() {
        return MAXIMUM_DIMENSION;
    }

    private void acquireResizePermit() {
        try {
            CONCURRENT_RESIZES.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Budget thumbnail generation was interrupted", exception);
        }
    }

    private BufferedImage decode(byte[] bytes) {
        try {
            ImageIO.setUseCache(false);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new IllegalArgumentException("Unsupported budget image format");
            }
            return image;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid budget image", exception);
        }
    }

    private BufferedImage scale(BufferedImage source, int targetSize, boolean widthOnly) {
        int measuredSize = widthOnly
                ? source.getWidth()
                : Math.max(source.getWidth(), source.getHeight());
        double scale = Math.min(
                1.0D,
                (double) targetSize / measuredSize);
        int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage current = source;
        int width = source.getWidth();
        int height = source.getHeight();

        do {
            width = Math.max(targetWidth, width / 2);
            height = Math.max(targetHeight, height / 2);
            current = resize(current, width, height);
        } while (width > targetWidth || height > targetHeight);

        return current;
    }

    private BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage thumbnail = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = thumbnail.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(
                    RenderingHints.KEY_COLOR_RENDERING,
                    RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return thumbnail;
    }

    BufferedImage applyExifOrientation(byte[] bytes, BufferedImage source) {
        return applyOrientation(source, readExifOrientation(bytes));
    }

    BufferedImage applyOrientation(BufferedImage source, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return source;
        }

        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        boolean swapsDimensions = orientation >= 5;
        int targetWidth = swapsDimensions ? sourceHeight : sourceWidth;
        int targetHeight = swapsDimensions ? sourceWidth : sourceHeight;
        int imageType = source.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;
        BufferedImage oriented = new BufferedImage(targetWidth, targetHeight, imageType);
        Graphics2D graphics = oriented.createGraphics();
        try {
            graphics.drawImage(source, orientationTransform(orientation, sourceWidth, sourceHeight), null);
        } finally {
            graphics.dispose();
        }
        return oriented;
    }

    int readExifOrientation(byte[] bytes) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            Metadata metadata = ImageMetadataReader.readMetadata(input);
            ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            Integer orientation = directory == null
                    ? null
                    : directory.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
            return orientation == null ? 1 : orientation;
        } catch (ImageProcessingException | IOException exception) {
            return 1;
        }
    }

    private AffineTransform orientationTransform(int orientation, int width, int height) {
        return switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
            default -> new AffineTransform();
        };
    }

    private byte[] encode(BufferedImage image) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("JPEG encoder is unavailable");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            if (parameters.canWriteCompressed()) {
                parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parameters.setCompressionQuality(JPEG_QUALITY);
            }
            if (parameters.canWriteProgressive()) {
                parameters.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            }
            writer.write(null, new IIOImage(image, null, null), parameters);
            imageOutput.flush();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to encode budget thumbnail", exception);
        } finally {
            writer.dispose();
        }
    }
}
