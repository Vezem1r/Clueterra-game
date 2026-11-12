package io.clueterra.api.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {

  private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif", "image/svg+xml");
  private static final Set<String> ALLOWED_AVATAR_DOMAINS = Set.of(
          "avatars.githubusercontent.com",
          "githubusercontent.com",
          "lh3.googleusercontent.com",
          "googleusercontent.com");
  private static final Set<String> ALLOWED_USER_AVATAR_EXTENSIONS = Set.of("jpg", "jpeg", "png");
  private static final Pattern VERSIONED_USER_AVATAR_KEY = Pattern.compile(
          "^[1-9][0-9]*/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(?:png|jpe?g)$");

  private static final Pattern PRIVATE_IP_PATTERN = Pattern.compile(
          "^(10\\.|172\\.(1[6-9]|2[0-9]|3[0-1])\\.|192\\.168\\.|127\\.|169\\.254\\.|::1|localhost)",
          Pattern.CASE_INSENSITIVE);
  private static final int MAX_REMOTE_REDIRECTS = 5;
  private static final String ASSET_VERSION_ALGORITHM = "HmacSHA256";
  private static final int ASSET_VERSION_BYTES = 16;
  private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
          .followRedirects(HttpClient.Redirect.NEVER)
          .connectTimeout(Duration.ofSeconds(10))
          .build();

  static final String HINT_IMAGES_BUCKET = "hint-images";
  static final String BUDGET_IMAGES_BUCKET = "budget-images";

  private final MinioClient minioClient;
  private final StorageProperties properties;

  public String upload(Long userId, InputStream data, long size, String contentType, String extension) {
    validateContentType(contentType);
    validateSize(size);

    String key = userId + "/" + UUID.randomUUID() + "." + extension;

    try {
      minioClient.putObject(PutObjectArgs.builder().bucket(properties.getBucket())
              .object(key)
              .stream(data, size, -1)
              .contentType(contentType)
              .build());
    } catch (Exception e) {
      log.error("Failed to upload file to MinIO: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to upload file", e);
    }

    return buildPublicBucketUrl(properties.getBucket(), key);
  }

  public void delete(String url) {
    if (url == null || url.isBlank()) return;

    String key = extractObjectKey(url, properties.getBucket());
    if (key == null || key.isBlank()) return;
    try {
      minioClient.removeObject(RemoveObjectArgs.builder()
              .bucket(properties.getBucket())
              .object(key)
              .build());
      if (VERSIONED_USER_AVATAR_KEY.matcher(key).matches()) {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(properties.getBucket())
                .object("leaderboard/128/" + key + ".jpg")
                .build());
      }
    } catch (Exception e) {
      log.error("Failed to delete object {}: {}", key, e.getMessage(), e);
    }
  }

  public String uploadToHintBucket(String objectPath, InputStream data, long size, String contentType) {
    validateContentType(contentType);
    try {
      minioClient.putObject(PutObjectArgs.builder()
              .bucket(HINT_IMAGES_BUCKET)
              .object(objectPath)
              .stream(data, size, -1)
              .contentType(contentType)
              .build());
    } catch (Exception e) {
      log.error("Failed to upload to wildlife bucket: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to upload file", e);
    }
    return buildPublicBucketUrl(HINT_IMAGES_BUCKET, objectPath);
  }

  public Optional<StorageObjectPayload> readFromHintBucket(String objectPath) {
    return readBucketObject(HINT_IMAGES_BUCKET, objectPath);
  }

  public String uploadToAvatarBucket(String objectPath, InputStream data, long size, String contentType) {
    validateContentType(contentType);
    validateSize(size);
    try {
      minioClient.putObject(PutObjectArgs.builder()
              .bucket(properties.getBucket())
              .object(objectPath)
              .stream(data, size, -1)
              .contentType(contentType)
              .build());
    } catch (Exception e) {
      log.error("Failed to upload to avatar bucket: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to upload file", e);
    }
    return buildPublicBucketUrl(properties.getBucket(), objectPath);
  }

  public Optional<StorageObjectPayload> readFromAvatarBucket(String objectPath) {
    return readBucketObject(properties.getBucket(), objectPath);
  }

  public String extractAvatarObjectKey(String imageUrl) {
    return extractObjectKey(imageUrl, properties.getBucket());
  }

  public void uploadToBudgetBucket(String objectPath, InputStream data, long size, String contentType) {
    validateContentType(contentType);
    try {
      minioClient.putObject(PutObjectArgs.builder()
              .bucket(BUDGET_IMAGES_BUCKET)
              .object(objectPath)
              .stream(data, size, -1)
              .contentType(contentType)
              .build());
    } catch (Exception e) {
      log.error("Failed to upload to budget image bucket: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to upload file", e);
    }
  }

  public Optional<StorageObjectPayload> readFromBudgetBucket(String objectPath) {
    return readBucketObject(BUDGET_IMAGES_BUCKET, objectPath);
  }

  public boolean isMinioUrl(String url) {
    return extractObjectKey(url, properties.getBucket()) != null;
  }

  public String resolveBudgetVariantAssetUrl(Long variantId, String role, String objectKey) {
    if (variantId == null
        || variantId <= 0
        || role == null
        || role.isBlank()
        || objectKey == null
        || objectKey.isBlank()) {
      return null;
    }
    return "/api/assets/budget/variants/" + variantId + "/" + role.toLowerCase(Locale.ROOT);
  }

  public String resolveBudgetCandidateAssetUrl(
      String wikidataId, String category, String sourceSha1, String objectKey) {
    if (wikidataId == null
        || wikidataId.isBlank()
        || category == null
        || category.isBlank()
        || sourceSha1 == null
        || sourceSha1.isBlank()
        || objectKey == null
        || objectKey.isBlank()) {
      return null;
    }
    return "/api/assets/budget/candidates/" + wikidataId + "/" + category + "/" + sourceSha1;
  }

  public String resolveHintAssetUrl(Long hintId, String imageUrl) {
    return resolveQuestionImageAssetUrl("/api/assets/hints/", hintId, imageUrl);
  }

  public String resolveStatisticAssetUrl(Long statisticId, String imageUrl) {
    return resolveQuestionImageAssetUrl("/api/assets/statistics/", statisticId, imageUrl);
  }

  private String resolveQuestionImageAssetUrl(String assetPath, Long assetId, String imageUrl) {
    if (assetId == null || assetId <= 0 || imageUrl == null || imageUrl.isBlank()) {
      return null;
    }
    return assetPath + assetId + "?v=" + assetVersion(imageUrl);
  }

  private String assetVersion(String imageUrl) {
    try {
      Mac mac = Mac.getInstance(ASSET_VERSION_ALGORITHM);
      mac.init(new SecretKeySpec(
          properties.getSecretKey().getBytes(StandardCharsets.UTF_8), ASSET_VERSION_ALGORITHM));
      byte[] digest = mac.doFinal(imageUrl.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, ASSET_VERSION_BYTES);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to compute asset version", e);
    }
  }

  public String resolveAvatarUrl(String imageUrl) {
    if (imageUrl == null || imageUrl.isBlank()) return imageUrl;

    String key = extractObjectKey(imageUrl, properties.getBucket());
    if (key != null) {
      return buildPublicBucketUrl(properties.getBucket(), key);
    }

    return null;
  }

  public String resolveUserAvatarUrl(String imageUrl) {
    if (imageUrl == null || imageUrl.isBlank()) return imageUrl;

    String key = extractObjectKey(imageUrl, properties.getBucket());
    if (key != null && hasAllowedUserAvatarExtension(key)) {
      return buildPublicBucketUrl(properties.getBucket(), key);
    }

    return null;
  }

  public InputStream downloadFromUrl(String url) {
    try {
      URI current = URI.create(url);
      for (int redirect = 0; redirect <= MAX_REMOTE_REDIRECTS; redirect++) {
        if (!isAllowedAvatarUri(current)) {
          return null;
        }

        HttpRequest request =
            HttpRequest.newBuilder(current)
                .GET()
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "Clueterra/1.0")
                .build();
        HttpResponse<InputStream> response =
            HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
          int status = response.statusCode();
          if (status >= 300 && status < 400) {
            Optional<String> location = response.headers().firstValue("Location");
            if (location.isEmpty()) {
              return null;
            }
            current = current.resolve(location.get());
            continue;
          }
          if (status != 200 || body == null) {
            return null;
          }
          long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
          if (contentLength > properties.getMaxHintImageSize()) {
            return null;
          }
          return new ByteArrayInputStream(readBounded(body, properties.getMaxHintImageSize()));
        }
      }
      log.warn("Too many redirects while downloading avatar");
      return null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Avatar download interrupted");
      return null;
    } catch (Exception e) {
      log.warn("Failed to download avatar: {}", e.getClass().getSimpleName());
      return null;
    }
  }

  public Optional<StorageObjectPayload> downloadRemoteImage(String url) {
    try {
      URI current = URI.create(url);
      for (int redirect = 0; redirect <= MAX_REMOTE_REDIRECTS; redirect++) {
        if (isBlockedRemoteUri(current)) {
          return Optional.empty();
        }

        HttpRequest request =
            HttpRequest.newBuilder(current)
                .GET()
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "Clueterra/1.0")
                .build();

        HttpResponse<InputStream> response =
            HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
          int status = response.statusCode();
          if (status >= 300 && status < 400) {
            Optional<String> location = response.headers().firstValue("Location");
            if (location.isEmpty()) {
              log.warn("Remote image redirect without Location header: {}", current);
              return Optional.empty();
            }
            current = current.resolve(location.get());
            continue;
          }

          if (status != 200 || body == null) {
            log.warn("Failed to download remote image from {}: HTTP {}", current, status);
            return Optional.empty();
          }

          long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
          if (contentLength > properties.getMaxHintImageSize()) {
            log.warn("Rejected oversized remote image from {}: {} bytes", current, contentLength);
            return Optional.empty();
          }

          String contentType =
              normalizeContentType(response.headers().firstValue("Content-Type").orElse(null));
          validateContentType(contentType);
          byte[] bytes = readBounded(body, properties.getMaxHintImageSize());
          if (bytes.length == 0) {
            return Optional.empty();
          }
          return Optional.of(new StorageObjectPayload(bytes, contentType));
        }
      }
      log.warn("Too many redirects while downloading remote image from {}", url);
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Remote image download interrupted: {}", url);
      return Optional.empty();
    } catch (Exception e) {
      log.warn("Failed to download remote image from {}: {}", url, e.getMessage());
      return Optional.empty();
    }
  }

  public String extractHintObjectKey(String imageUrl) {
    return extractObjectKey(imageUrl, HINT_IMAGES_BUCKET);
  }

  private void validateContentType(String contentType) {
    validateBucketContentType(contentType);
  }

  private void validateSize(long size) {
    if (size > properties.getMaxFileSize()) {
      throw new IllegalArgumentException(
          "File too large. Max size: " + properties.getMaxFileSize() + " bytes");
    }
  }

  private void validateHintImageSize(long size) {
    if (size > properties.getMaxHintImageSize()) {
      throw new IllegalArgumentException(
          "File too large. Max size: " + properties.getMaxHintImageSize() + " bytes");
    }
  }

  private void validateBucketContentType(String contentType) {
    String normalizedContentType = normalizeContentType(contentType);
    if (!StorageService.ALLOWED_CONTENT_TYPES.contains(normalizedContentType)) {
      throw new IllegalArgumentException("Unsupported file type: " + contentType);
    }
  }

  private String buildPublicBucketUrl(String bucket, String key) {
    return trimTrailingSlash(properties.getPublicEndpoint()) + "/" + bucket + "/" + key;
  }

  private Optional<StorageObjectPayload> readBucketObject(String bucket, String objectPath) {
    if (objectPath == null || objectPath.isBlank()) {
      return Optional.empty();
    }
    try {
      var metadata =
          minioClient.statObject(
              StatObjectArgs.builder().bucket(bucket).object(objectPath).build());
      if (metadata.size() <= 0 || metadata.size() > properties.getMaxHintImageSize()) {
        log.warn(
            "Rejected invalid object size for {}/{}: {} bytes",
            bucket,
            objectPath,
            metadata.size());
        return Optional.empty();
      }
      String contentType = normalizeContentType(metadata.contentType());
      validateBucketContentType(contentType);
      try (InputStream stream =
          minioClient.getObject(
              GetObjectArgs.builder().bucket(bucket).object(objectPath).build())) {
        return Optional.of(
            new StorageObjectPayload(
                readBounded(stream, properties.getMaxHintImageSize()), contentType));
      }
    } catch (Exception e) {
      log.debug("Object {}/{} not found in MinIO cache: {}", bucket, objectPath, e.getMessage());
      return Optional.empty();
    }
  }

  private String extractObjectKey(String url, String bucket) {
    if (url == null || url.isBlank()) return null;

    if (isLocalAssetPath(url)) {
      return null;
    }

    for (String prefix : bucketPrefixes(bucket)) {
      if (url.startsWith(prefix)) {
        return url.substring(prefix.length());
      }
    }

    if (!url.startsWith("http://") && !url.startsWith("https://")) {
      return url;
    }

    return null;
  }

  private boolean isLocalAssetPath(String url) {
    return url.startsWith("/api/assets/");
  }

  private Set<String> bucketPrefixes(String bucket) {
    Set<String> prefixes = new LinkedHashSet<>();
    prefixes.add(trimTrailingSlash(properties.getEndpoint()) + "/" + bucket + "/");
    prefixes.add(trimTrailingSlash(properties.getPublicEndpoint()) + "/" + bucket + "/");
    return prefixes;
  }

  private String trimTrailingSlash(String value) {
    if (value == null || value.isBlank()) return "";
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private boolean isBlockedRemoteUri(URI uri) {
    String scheme = uri.getScheme();
    if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
      log.warn("Rejected non-HTTP image URL scheme: {}", scheme);
      return true;
    }
    String host = uri.getHost();
    if (host == null || PRIVATE_IP_PATTERN.matcher(host).find() || resolvesToPrivateAddress(host)) {
      log.warn("Rejected private/loopback image URL host: {}", host);
      return true;
    }
    return false;
  }

  private boolean isAllowedAvatarUri(URI uri) {
    if (isBlockedRemoteUri(uri)) {
      return false;
    }
    String host = uri.getHost().toLowerCase(Locale.ROOT);
    boolean allowed =
        ALLOWED_AVATAR_DOMAINS.stream()
            .anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
    if (!allowed) {
      log.warn("Rejected avatar URL from untrusted domain: {}", host);
    }
    return allowed;
  }

  private boolean resolvesToPrivateAddress(String host) {
    try {
      return Arrays.stream(InetAddress.getAllByName(host)).anyMatch(this::isPrivateAddress);
    } catch (Exception exception) {
      log.warn("Failed to resolve remote image host {}: {}", host, exception.getMessage());
      return true;
    }
  }

  private boolean isPrivateAddress(InetAddress address) {
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      return true;
    }
    byte[] bytes = address.getAddress();
    return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
  }

  private String normalizeContentType(String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return "";
    }
    int semicolonIndex = contentType.indexOf(';');
    String normalized =
        semicolonIndex >= 0 ? contentType.substring(0, semicolonIndex) : contentType;
    return normalized.trim().toLowerCase(Locale.ROOT);
  }

  private byte[] readBounded(InputStream stream, long maximumBytes) throws IOException {
    if (maximumBytes <= 0 || maximumBytes >= Integer.MAX_VALUE) {
      throw new IllegalStateException("Invalid maximum image size: " + maximumBytes);
    }
    byte[] bytes = stream.readNBytes((int) maximumBytes + 1);
    validateHintImageSize(bytes.length);
    return bytes;
  }

  private boolean hasAllowedUserAvatarExtension(String key) {
    int dotIndex = key.lastIndexOf('.');
    if (dotIndex < 0 || dotIndex == key.length() - 1) {
      return false;
    }
    String extension = key.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    return ALLOWED_USER_AVATAR_EXTENSIONS.contains(extension);
  }
}
