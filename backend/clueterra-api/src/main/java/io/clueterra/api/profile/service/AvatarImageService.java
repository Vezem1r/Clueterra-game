package io.clueterra.api.profile.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import io.clueterra.api.storage.StorageProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AvatarImageService {

    private static final int AVATAR_SIZE = 512;
    private static final int MAX_SOURCE_DIMENSION = 4096;
    private static final String JPEG_CONTENT_TYPE = "image/jpeg";
    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final String OUTPUT_CONTENT_TYPE = PNG_CONTENT_TYPE;
    private static final String OUTPUT_EXTENSION = "png";
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(JPEG_CONTENT_TYPE, PNG_CONTENT_TYPE);
    private static final byte[] PNG_SIGNATURE = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private final StorageProperties storageProperties;

    public ProcessedAvatar process(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Avatar file is required");
        }
        if (file.getSize() > storageProperties.getMaxFileSize()) {
            throw new IllegalArgumentException(
                    "File too large. Max size: " + storageProperties.getMaxFileSize() + " bytes");
        }

        String contentType = normalizeContentType(file.getContentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported avatar file type. Use JPEG or PNG.");
        }

        byte[] input = readBytes(file);
        return process(input, contentType);
    }

    public ProcessedAvatar process(byte[] input) {
        validateBytes(input);
        String contentType = detectContentType(input);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported avatar file type. Use JPEG or PNG.");
        }
        return process(input, contentType);
    }

    private ProcessedAvatar process(byte[] input, String contentType) {
        validateBytes(input);
        if (!matchesContentType(input, contentType)) {
            throw new IllegalArgumentException("Uploaded avatar content does not match its file type");
        }

        BufferedImage source = decodeImage(input);
        BufferedImage avatar = cropToSquareAvatar(source);
        return new ProcessedAvatar(encodePng(avatar), OUTPUT_CONTENT_TYPE, OUTPUT_EXTENSION);
    }

    private void validateBytes(byte[] input) {
        if (input == null || input.length == 0) {
            throw new IllegalArgumentException("Avatar file is required");
        }
        if (input.length > storageProperties.getMaxFileSize()) {
            throw new IllegalArgumentException(
                    "File too large. Max size: " + storageProperties.getMaxFileSize() + " bytes");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read uploaded avatar", e);
        }
    }

    private BufferedImage decodeImage(byte[] input) {
        ImageIO.setUseCache(false);
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            Iterator<ImageReader> readers = stream == null ? null : ImageIO.getImageReaders(stream);
            if (readers == null || !readers.hasNext()) {
                throw new IllegalArgumentException("Invalid avatar image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                validateDimensions(reader.getWidth(0), reader.getHeight(0));
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid avatar image", e);
        }
    }

    private void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid avatar image");
        }
        if (width > MAX_SOURCE_DIMENSION || height > MAX_SOURCE_DIMENSION) {
            throw new IllegalArgumentException(
                    "Avatar image is too large. Max dimensions: " + MAX_SOURCE_DIMENSION + "x" + MAX_SOURCE_DIMENSION);
        }
    }

    private BufferedImage cropToSquareAvatar(BufferedImage source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        int sourceX = (source.getWidth() - side) / 2;
        int sourceY = (source.getHeight() - side) / 2;

        BufferedImage target = new BufferedImage(AVATAR_SIZE, AVATAR_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(
                    source,
                    0,
                    0,
                    AVATAR_SIZE,
                    AVATAR_SIZE,
                    sourceX,
                    sourceY,
                    sourceX + side,
                    sourceY + side,
                    null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private byte[] encodePng(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, OUTPUT_EXTENSION, output)) {
                throw new IllegalStateException("PNG encoder is unavailable");
            }
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode avatar image", e);
        }
    }

    private boolean matchesContentType(byte[] input, String contentType) {
        if (JPEG_CONTENT_TYPE.equals(contentType)) {
            return input.length >= 3
                    && (input[0] & 0xFF) == 0xFF
                    && (input[1] & 0xFF) == 0xD8
                    && (input[2] & 0xFF) == 0xFF;
        }
        if (PNG_CONTENT_TYPE.equals(contentType)) {
            if (input.length < PNG_SIGNATURE.length) {
                return false;
            }
            for (int i = 0; i < PNG_SIGNATURE.length; i++) {
                if (input[i] != PNG_SIGNATURE[i]) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private String detectContentType(byte[] input) {
        if (matchesContentType(input, JPEG_CONTENT_TYPE)) {
            return JPEG_CONTENT_TYPE;
        }
        if (matchesContentType(input, PNG_CONTENT_TYPE)) {
            return PNG_CONTENT_TYPE;
        }
        return "";
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "";
        }
        int semicolonIndex = contentType.indexOf(';');
        String normalized = semicolonIndex >= 0 ? contentType.substring(0, semicolonIndex) : contentType;
        return normalized.trim().toLowerCase(Locale.ROOT);
    }
}
