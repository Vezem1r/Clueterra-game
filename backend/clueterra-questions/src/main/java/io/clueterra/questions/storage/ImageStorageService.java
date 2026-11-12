package io.clueterra.questions.storage;

import java.time.Clock;
import io.hypersistence.tsid.TSID;
import io.minio.GetObjectArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "clueterra.storage", name = "enabled", havingValue = "true")
public class ImageStorageService {

    static final String HINT_IMAGES_BUCKET = "hint-images";
    static final String BUDGET_IMAGES_BUCKET = "budget-images";
    private static final int MAX_REDIRECTS = 5;
    private static final Set<String> PASS_THROUGH_EXTENSIONS = Set.of("svg", "gif", "webp");
    private static final Set<String> JPEG_EXTENSIONS = Set.of("jpg", "jpeg");
    private static final String JPEG_FORMAT = "jpg";
    private static final String PNG_FORMAT = "png";
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int MAX_CONFIGURED_RETRIES = 5;
    private static final long MAX_RETRY_DELAY_MS = Duration.ofMinutes(10).toMillis();
    private static final long WAIT_CHUNK_MS = Duration.ofSeconds(30).toMillis();
    private static final String USER_AGENT = "Clueterra/1.0 (https://clueterra.com; support@clueterra.com)";

    private final MinioClient minioClient;
    private final ImageStorageProperties properties;
    private final Clock clock;
    private final Object wikimediaDownloadLock = new Object();
    private long nextWikimediaDownloadAtMs;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public Optional<String> store(String sourceUrl, String source) {
        if (sourceUrl == null || sourceUrl.isBlank()) return Optional.empty();
        String objectPath = source + "/" + TSID.Factory.getTsid().toString().toLowerCase();
        return downloadAndStore(sourceUrl, objectPath);
    }

    public Optional<String> storeDirect(String sourceUrl, String source) {
        if (sourceUrl == null || sourceUrl.isBlank()) return Optional.empty();
        try {
            URI sourceUri = normalizeDownloadUri(sourceUrl);
            DownloadedImage response = sendImageRequestWithRetry(sourceUri);
            if (response.statusCode() != 200 || response.tooLarge() || !response.hasBody()) {
                log.debug("Failed to download panorama from {}: HTTP {}, too large: {}",
                        sourceUrl, response.statusCode(), response.tooLarge());
                return Optional.empty();
            }

            String contentType = response.contentType();
            if (!isImageContentType(contentType)) {
                log.debug("Rejected panorama from {}: non-image Content-Type {}", sourceUrl, contentType);
                return Optional.empty();
            }
            String ext = extensionFromContentType(contentType);
            if (ext == null) ext = JPEG_FORMAT;
            String objectPath = source + "/" + TSID.Factory.getTsid().toString().toLowerCase() + "." + ext;

            if (exists(objectPath)) {
                return Optional.of(objectPath);
            }

            try (InputStream body = new ByteArrayInputStream(response.body())) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(HINT_IMAGES_BUCKET)
                        .object(objectPath)
                        .stream(body, response.body().length, -1)
                        .contentType(contentType)
                        .build());
            }

            log.debug("Stored panorama (no compression): {} → {}", sourceUrl, publicUrl(objectPath));
            return Optional.of(objectPath);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Failed to store panorama (interrupted): {}", sourceUrl);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to store panorama from {}: [{}]", sourceUrl, e.getClass().getSimpleName(), e);
            return Optional.empty();
        }
    }

    public Optional<String> downloadAndStore(String sourceUrl, String objectPath) {
        ImageDownloadResult result = downloadAndStoreDetailed(sourceUrl, objectPath);
        return result.success() ? Optional.of(result.objectPath()) : Optional.empty();
    }

    public ImageDownloadResult downloadAndStoreDetailed(String sourceUrl, String objectPath) {
        return downloadAndStoreDetailed(HINT_IMAGES_BUCKET, sourceUrl, objectPath);
    }

    public ImageDownloadResult downloadAndStoreDetailed(String bucket, String sourceUrl, String objectPath) {
        try {
            if (sourceUrl == null || sourceUrl.isBlank()) {
                return ImageDownloadResult.failed(null, "Source image URL is blank");
            }
            if (objectPath == null || objectPath.isBlank()) {
                return ImageDownloadResult.failed(null, "Storage object path is blank");
            }
            String requestedExtension = extractExtension(objectPath);
            if (requestedExtension != null && exists(bucket, objectPath)) {
                return ImageDownloadResult.stored(objectPath, 200);
            }

            URI sourceUri = normalizeDownloadUri(sourceUrl);
            DownloadedImage response = sendImageRequestWithRetry(sourceUri);

            if (response.statusCode() != 200) {
                log.debug("Failed to download image from {}: HTTP {}", sourceUrl, response.statusCode());
                String retrySuffix = response.statusCode() == TOO_MANY_REQUESTS ? " after rate-limit retries" : "";
                return ImageDownloadResult.failed(
                        response.statusCode(),
                        "Source returned HTTP " + response.statusCode() + retrySuffix);
            }

            String contentType = response.contentType();
            if (response.tooLarge()) {
                return ImageDownloadResult.failed(
                        response.statusCode(),
                        "Image exceeds the configured download limit of " + properties.getMaxDownloadBytes() + " bytes");
            }
            if (!response.hasBody()) {
                return ImageDownloadResult.failed(response.statusCode(), "Source returned an empty response body");
            }
            if (response.body().length > properties.getMaxDownloadBytes()) {
                return ImageDownloadResult.failed(
                        response.statusCode(),
                        "Image exceeds the configured download limit of " + properties.getMaxDownloadBytes() + " bytes");
            }
            if (!isImageContentType(contentType)) {
                return ImageDownloadResult.failed(
                        response.statusCode(),
                        "Source returned non-image Content-Type " + contentType);
            }
            PreparedImageObject prepared = prepareForStorage(objectPath, sourceUrl, contentType, response.body());
            if (exists(bucket, prepared.objectPath())) {
                return ImageDownloadResult.stored(prepared.objectPath(), response.statusCode());
            }

            try (InputStream body = new ByteArrayInputStream(prepared.bytes())) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(prepared.objectPath())
                        .stream(body, prepared.bytes().length, -1)
                        .contentType(prepared.contentType())
                        .build());
            }

            log.debug("Stored image: {} → {}", sourceUrl, publicUrl(bucket, prepared.objectPath()));
            return ImageDownloadResult.stored(
                    prepared.objectPath(),
                    response.statusCode(),
                    prepared.bytes().length,
                    sha256(prepared.bytes()),
                    prepared.contentType());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Failed to store hint image from {}: interrupted", sourceUrl);
            return ImageDownloadResult.failed(null, "Image download was interrupted");
        } catch (Exception e) {
            log.warn("Failed to store hint image from {}", sourceUrl, e);
            return ImageDownloadResult.failed(null, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public Optional<String> storeBytes(byte[] bytes, String objectPath, String contentType) {
        if (bytes == null || bytes.length == 0
                || objectPath == null || objectPath.isBlank()
                || contentType == null || contentType.isBlank()) {
            return Optional.empty();
        }

        try {
            if (exists(objectPath)) {
                return Optional.of(objectPath);
            }

            try (InputStream body = new ByteArrayInputStream(bytes)) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(HINT_IMAGES_BUCKET)
                        .object(objectPath)
                        .stream(body, bytes.length, -1)
                        .contentType(contentType)
                        .build());
            }

            log.debug("Stored bundled hint image: {}", publicUrl(objectPath));
            return Optional.of(objectPath);
        } catch (Exception e) {
            log.warn("Failed to store bundled hint image {}", objectPath, e);
            return Optional.empty();
        }
    }

    public Optional<String> copyBudgetProjectImage(String sourceObjectKey, String targetObjectKey) {
        if (sourceObjectKey == null || sourceObjectKey.isBlank()
                || targetObjectKey == null || targetObjectKey.isBlank()
                || sourceObjectKey.contains("..") || targetObjectKey.contains("..")) {
            return Optional.empty();
        }
        try {
            if (!exists(targetObjectKey)) {
                minioClient.copyObject(CopyObjectArgs.builder()
                        .bucket(HINT_IMAGES_BUCKET)
                        .object(targetObjectKey)
                        .source(CopySource.builder()
                                .bucket(BUDGET_IMAGES_BUCKET)
                                .object(sourceObjectKey)
                                .build())
                        .build());
            }
            return Optional.of(targetObjectKey);
        } catch (Exception e) {
            log.warn("Failed to copy Budget project image {}", sourceObjectKey, e);
            return Optional.empty();
        }
    }

    public List<StoredImageObject> listStoredObjects(String bucket) {
        List<StoredImageObject> objects = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .recursive(true)
                            .build());
            for (Result<Item> result : results) {
                try {
                    Item item = result.get();
                    objects.add(new StoredImageObject(
                            item.objectName(),
                            item.lastModified() != null ? item.lastModified().toInstant() : null));
                } catch (Exception e) {
                    log.warn("Failed to read object entry", e);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list objects in bucket", e);
        }
        return objects;
    }

    public void verifyBucketAccess(String bucket) {
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .maxKeys(1)
                            .build());
            Iterator<Result<Item>> iterator = results.iterator();
            if (iterator.hasNext()) {
                iterator.next().get();
            }
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Media maintenance cannot access bucket " + bucket,
                    exception);
        }
    }

    public boolean deleteObject(String bucket, String objectPath) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectPath)
                    .build());
            return true;
        } catch (Exception e) {
            log.warn("Failed to delete object {}", objectPath, e);
            return false;
        }
    }

    public String extractObjectPath(String dbValue) {
        return extractObjectPath(HINT_IMAGES_BUCKET, dbValue);
    }

    public String extractObjectPath(String bucket, String dbValue) {
        if (dbValue == null || dbValue.isBlank()) return null;
        for (String prefix : bucketPrefixes(bucket)) {
            if (dbValue.startsWith(prefix)) {
                return dbValue.substring(prefix.length());
            }
        }
        if (!dbValue.startsWith("/")
                && !dbValue.startsWith("http://")
                && !dbValue.startsWith("https://")) {
            return dbValue;
        }
        return null;
    }

    public Optional<byte[]> readObjectBytes(String objectPath) {
        return readObjectBytes(HINT_IMAGES_BUCKET, objectPath);
    }

    public Optional<byte[]> readObjectBytes(String bucket, String objectPath) {
        if (objectPath == null || objectPath.isBlank()) {
            return Optional.empty();
        }
        try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectPath)
                .build())) {
            return Optional.of(stream.readAllBytes());
        } catch (Exception exception) {
            log.warn("Failed to read {}/{} from object storage", bucket, objectPath, exception);
            return Optional.empty();
        }
    }

    public boolean objectExistsStrict(String bucket, String objectPath) {
        if (objectPath == null || objectPath.isBlank()) {
            return false;
        }
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectPath)
                    .build());
            return true;
        } catch (ErrorResponseException exception) {
            String errorCode = exception.errorResponse() != null
                    ? exception.errorResponse().code()
                    : null;
            if ("NoSuchKey".equals(errorCode) || "NoSuchObject".equals(errorCode)) {
                return false;
            }
            throw storageAccessFailure(bucket, objectPath, exception);
        } catch (Exception exception) {
            throw storageAccessFailure(bucket, objectPath, exception);
        }
    }

    private DownloadedImage sendImageRequestWithRetry(URI sourceUri) throws IOException, InterruptedException {
        int maxRetries = Math.clamp(properties.getDownloadMaxRetries(), 0, MAX_CONFIGURED_RETRIES);
        for (int attempt = 0; ; attempt++) {
            awaitWikimediaDownloadPermit(sourceUri);
            HttpResponse<InputStream> response = sendFollowingRedirects(sourceUri);
            if (response.statusCode() != TOO_MANY_REQUESTS) {
                return readLimited(response);
            }

            Duration retryDelay = retryDelay(response, attempt);
            if (isWikimediaHost(sourceUri)) {
                extendWikimediaCooldown(retryDelay);
            }
            if (attempt >= maxRetries) {
                return readLimited(response);
            }
            response.body().close();

            log.warn(
                    "Image source rate-limited {}. Retrying in {} ms (attempt {}/{})",
                    sourceUri.getHost(),
                    retryDelay.toMillis(),
                    attempt + 1,
                    maxRetries);
            if (!isWikimediaHost(sourceUri)) {
                sleepInChunks(retryDelay);
            }
        }
    }

    private HttpResponse<InputStream> sendFollowingRedirects(URI sourceUri) throws IOException, InterruptedException {
        URI current = sourceUri;
        for (int hop = 0; ; hop++) {
            ImageSourceGuard.requirePublicHttpUri(current);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(current)
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .header("Accept", "image/*")
                    .header("User-Agent", USER_AGENT)
                    .build();
            HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            Optional<String> location = response.headers().firstValue("Location");
            if (status < 300 || status >= 400 || location.isEmpty()) {
                return response;
            }
            response.body().close();
            if (hop >= MAX_REDIRECTS) {
                throw new IOException("Too many redirects downloading " + sourceUri);
            }
            current = current.resolve(location.get());
        }
    }

    private DownloadedImage readLimited(HttpResponse<InputStream> response) throws IOException {
        long maxBytes = properties.getMaxDownloadBytes();
        try (InputStream body = response.body()) {
            OptionalLong declared = response.headers().firstValueAsLong("Content-Length");
            if (declared.isPresent() && declared.getAsLong() > maxBytes) {
                return new DownloadedImage(response.statusCode(), response.headers(), new byte[0], true);
            }
            byte[] bytes = body.readNBytes((int) Math.min(maxBytes + 1, Integer.MAX_VALUE - 8));
            boolean tooLarge = bytes.length > maxBytes;
            return new DownloadedImage(response.statusCode(), response.headers(), tooLarge ? new byte[0] : bytes, tooLarge);
        }
    }

    private void awaitWikimediaDownloadPermit(URI sourceUri) throws InterruptedException {
        if (!isWikimediaHost(sourceUri)) {
            return;
        }
        synchronized (wikimediaDownloadLock) {
            while (true) {
                long now = System.currentTimeMillis();
                long waitMs = nextWikimediaDownloadAtMs - now;
                if (waitMs <= 0) {
                    long intervalMs = Math.max(0, properties.getWikimediaDownloadIntervalMs());
                    nextWikimediaDownloadAtMs = now + intervalMs;
                    return;
                }
                wikimediaDownloadLock.wait(Math.min(waitMs, WAIT_CHUNK_MS));
            }
        }
    }

    private void extendWikimediaCooldown(Duration delay) {
        synchronized (wikimediaDownloadLock) {
            long cooldownUntil = System.currentTimeMillis() + delay.toMillis();
            nextWikimediaDownloadAtMs = Math.max(nextWikimediaDownloadAtMs, cooldownUntil);
        }
    }

    private void sleepInChunks(Duration delay) throws InterruptedException {
        long remainingMs = delay.toMillis();
        while (remainingMs > 0) {
            long chunkMs = Math.min(remainingMs, WAIT_CHUNK_MS);
            Thread.sleep(chunkMs);
            remainingMs -= chunkMs;
        }
    }

    private Duration retryDelay(HttpResponse<?> response, int attempt) {
        Optional<String> retryAfter = response.headers().firstValue("Retry-After");
        if (retryAfter.isPresent()) {
            Duration requested = parseRetryAfter(retryAfter.get(), Instant.now(clock));
            if (!requested.isZero() && !requested.isNegative()) {
                return requested.compareTo(Duration.ofMillis(MAX_RETRY_DELAY_MS)) > 0
                        ? Duration.ofMillis(MAX_RETRY_DELAY_MS)
                        : requested;
            }
        }

        long baseDelayMs = Math.max(1000, properties.getDownloadRetryBaseDelayMs());
        long multiplier = 1L << Math.min(attempt, MAX_CONFIGURED_RETRIES);
        return Duration.ofMillis(Math.min(MAX_RETRY_DELAY_MS, baseDelayMs * multiplier));
    }

    static Duration parseRetryAfter(String value, Instant now) {
        if (value == null || value.isBlank()) {
            return Duration.ZERO;
        }
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(value.trim())));
        } catch (NumberFormatException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                Duration delay = Duration.between(now, retryAt);
                return delay.isNegative() ? Duration.ZERO : delay;
            } catch (DateTimeParseException invalidDate) {
                return Duration.ZERO;
            }
        }
    }

    public static URI normalizeDownloadUri(String sourceUrl) {
        URI sourceUri = URI.create(sourceUrl);
        if (!"upload.wikimedia.org".equalsIgnoreCase(sourceUri.getHost())) {
            return sourceUri;
        }
        return URI.create(sourceUri.getScheme() + "://" + sourceUri.getRawAuthority() + sourceUri.getRawPath());
    }

    private static boolean isWikimediaHost(URI sourceUri) {
        String host = sourceUri.getHost();
        return host != null && (host.equalsIgnoreCase("wikimedia.org")
                || host.toLowerCase(Locale.ROOT).endsWith(".wikimedia.org"));
    }

    private boolean exists(String objectPath) {
        return exists(HINT_IMAGES_BUCKET, objectPath);
    }

    private boolean exists(String bucket, String objectPath) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectPath)
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String publicUrl(String objectPath) {
        return publicUrl(HINT_IMAGES_BUCKET, objectPath);
    }

    private MediaStorageAccessException storageAccessFailure(
            String bucket,
            String objectPath,
            Exception cause) {
        return new MediaStorageAccessException(
                "Failed to inspect media object " + bucket + "/" + objectPath,
                cause);
    }

    private String publicUrl(String bucket, String objectPath) {
        return trimTrailingSlash(resolvePublicEndpoint()) + "/" + bucket + "/" + objectPath;
    }

    private Set<String> bucketPrefixes(String bucket) {
        Set<String> prefixes = new LinkedHashSet<>();
        prefixes.add(trimTrailingSlash(properties.getEndpoint()) + "/" + bucket + "/");
        prefixes.add(trimTrailingSlash(resolvePublicEndpoint()) + "/" + bucket + "/");
        return prefixes;
    }

    private String resolvePublicEndpoint() {
        return properties.getPublicEndpoint() != null && !properties.getPublicEndpoint().isBlank()
                ? properties.getPublicEndpoint()
                : properties.getEndpoint();
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    PreparedImageObject prepareForStorage(String objectPath, String sourceUrl, String contentType, byte[] body) {
        if (body == null || body.length == 0) {
            throw new IllegalArgumentException("Image payload must not be empty");
        }

        String requestedExtension = extractExtension(objectPath);
        String normalizedExt = normalizeExtension(requestedExtension);
        if (!isImageContentType(contentType) || (normalizedExt != null && PASS_THROUGH_EXTENSIONS.contains(normalizedExt))) {
            return passthroughObject(objectPath, sourceUrl, contentType, body);
        }

        if (!properties.isCompressRasterImages()) {
            return passthroughObject(objectPath, sourceUrl, contentType, body);
        }

        try {
            BufferedImage sourceImage = ImageIO.read(new ByteArrayInputStream(body));
            if (sourceImage == null) {
                return passthroughObject(objectPath, sourceUrl, contentType, body);
            }

            boolean hasAlpha = sourceImage.getColorModel() != null && sourceImage.getColorModel().hasAlpha();
            String targetFormat = resolveTargetFormat(requestedExtension, hasAlpha);
            BufferedImage normalized = normalizeImage(sourceImage, targetFormat);
            byte[] encoded = encodeImage(normalized, targetFormat);
            return new PreparedImageObject(
                    ensureExtension(objectPath, targetFormat),
                    mimeTypeFor(targetFormat),
                    encoded
            );
        } catch (Exception e) {
            log.debug("Falling back to original image bytes for {}: {}", sourceUrl, e.getMessage());
            return passthroughObject(objectPath, sourceUrl, contentType, body);
        }
    }

    private PreparedImageObject passthroughObject(
            String objectPath,
            String sourceUrl,
            String contentType,
            byte[] body) {
        String requestedExtension = extractExtension(objectPath);
        String fallbackExtension = determineFallbackExtension(contentType, sourceUrl);
        return new PreparedImageObject(
                requestedExtension != null ? objectPath : ensureExtension(objectPath, fallbackExtension),
                normalizeContentType(contentType, fallbackExtension),
                body
        );
    }

    private BufferedImage normalizeImage(BufferedImage sourceImage, String targetFormat) {
        int maxWidth = Math.max(1, properties.getMaxWidth());
        int maxHeight = Math.max(1, properties.getMaxHeight());

        int sourceWidth = Math.max(1, sourceImage.getWidth());
        int sourceHeight = Math.max(1, sourceImage.getHeight());
        double scale = Math.min(1.0, Math.min((double) maxWidth / sourceWidth, (double) maxHeight / sourceHeight));

        int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
        int imageType = JPEG_FORMAT.equals(targetFormat) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;

        BufferedImage output = new BufferedImage(targetWidth, targetHeight, imageType);
        Graphics2D graphics = output.createGraphics();
        try {
            if (JPEG_FORMAT.equals(targetFormat)) {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, targetWidth, targetHeight);
            } else {
                graphics.setComposite(AlphaComposite.Clear);
                graphics.fillRect(0, 0, targetWidth, targetHeight);
                graphics.setComposite(AlphaComposite.SrcOver);
            }
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(sourceImage, 0, 0, targetWidth, targetHeight, null);
            return output;
        } finally {
            graphics.dispose();
        }
    }

    private byte[] encodeImage(BufferedImage image, String targetFormat) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (JPEG_FORMAT.equals(targetFormat)) {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(JPEG_FORMAT);
            if (!writers.hasNext()) {
                throw new IllegalStateException("No JPEG writer available");
            }

            ImageWriter writer = writers.next();
            try (MemoryCacheImageOutputStream imageOutput = new MemoryCacheImageOutputStream(output)) {
                writer.setOutput(imageOutput);
                ImageWriteParam params = writer.getDefaultWriteParam();
                if (params.canWriteCompressed()) {
                    params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    params.setCompressionQuality(clampJpegQuality(properties.getJpegQuality()));
                }
                writer.write(null, new IIOImage(image, null, null), params);
            } finally {
                writer.dispose();
            }
            return output.toByteArray();
        }

        if (!ImageIO.write(image, targetFormat, output)) {
            throw new IllegalStateException("No writer available for format " + targetFormat);
        }
        return output.toByteArray();
    }

    private static boolean isImageContentType(String contentType) {
        String normalized = normalizeMimeType(contentType);
        return normalized != null && normalized.startsWith("image/");
    }

    private static float clampJpegQuality(float quality) {
        if (Float.isNaN(quality)) {
            return 0.82f;
        }
        return Math.clamp(quality, 0.1f, 1.0f);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String resolveTargetFormat(String requestedExtension, boolean hasAlpha) {
        String normalized = normalizeExtension(requestedExtension);
        if (normalized != null && JPEG_EXTENSIONS.contains(normalized)) {
            return JPEG_FORMAT;
        }
        if (PNG_FORMAT.equals(normalized)) {
            return PNG_FORMAT;
        }
        return hasAlpha ? PNG_FORMAT : JPEG_FORMAT;
    }

    private static String determineFallbackExtension(String contentType, String sourceUrl) {
        String mimeExtension = extensionFromContentType(contentType);
        if (mimeExtension != null) {
            return mimeExtension;
        }
        String urlExtension = extractExtension(sourceUrl);
        return urlExtension != null ? urlExtension : JPEG_FORMAT;
    }

    private static String normalizeContentType(String contentType, String extension) {
        if (isImageContentType(contentType)) {
            return contentType;
        }
        return mimeTypeFor(extension);
    }

    private static String mimeTypeFor(String extension) {
        return switch (normalizeExtension(extension)) {
            case PNG_FORMAT -> "image/png";
            case "svg" -> "image/svg+xml";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }

    private static String extensionFromContentType(String contentType) {
        String normalized = normalizeMimeType(contentType);
        if (normalized == null) {
            return null;
        }
        return switch (normalized) {
            case "image/png" -> PNG_FORMAT;
            case "image/svg+xml" -> "svg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/jpg", "image/jpeg", "image/pjpeg" -> JPEG_FORMAT;
            default -> null;
        };
    }

    private static String ensureExtension(String objectPath, String extension) {
        String normalizedExtension = normalizeExtension(extension);
        String existingExtension = extractExtension(objectPath);
        if (existingExtension != null) {
            return basePath(objectPath) + "." + normalizedExtension;
        }
        return objectPath + "." + normalizedExtension;
    }

    private static String basePath(String path) {
        int lastSlash = path.lastIndexOf('/');
        int lastDot = path.lastIndexOf('.');
        if (lastDot <= lastSlash) {
            return path;
        }
        return path.substring(0, lastDot);
    }

    private static String extractExtension(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        int lastSlash = path.lastIndexOf('/');
        int lastDot = path.lastIndexOf('.');
        if (lastDot <= lastSlash || lastDot == path.length() - 1) {
            return null;
        }
        return normalizeExtension(path.substring(lastDot + 1));
    }

    private static String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return null;
        }
        String normalized = extension.trim().toLowerCase();
        return "jpeg".equals(normalized) ? JPEG_FORMAT : normalized;
    }

    private static String normalizeMimeType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        int separator = contentType.indexOf(';');
        String normalized = separator >= 0 ? contentType.substring(0, separator) : contentType;
        normalized = normalized.trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }

}
