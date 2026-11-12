package io.clueterra.api.admin.hints.image;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.storage.StorageProperties;

@Component
public class WikimediaCommonsImageClient {

    private static final String API_URL = "https://commons.wikimedia.org/w/api.php";
    private static final String COMMONS_COPYRIGHT_TAGS_URL =
            "https://commons.wikimedia.org/wiki/Commons:Copyright_tags";
    private static final String USER_AGENT = "Clueterra/1.0 (admin image maintenance)";
    private static final Set<String> ALLOWED_MIME_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> ALLOWED_IMAGE_HOSTS =
            Set.of("upload.wikimedia.org", "thumb.wikimedia.org");
    private static final int MINIMUM_WIDTH = 800;
    private static final int MINIMUM_HEIGHT = 450;
    private static final int THUMBNAIL_WIDTH = 1600;
    private static final int MAX_REDIRECTS = 5;

    private final ObjectMapper objectMapper;
    private final WikimediaLicensePolicy licensePolicy;
    private final StorageProperties storageProperties;
    private final HttpClient httpClient;

    public WikimediaCommonsImageClient(
            ObjectMapper objectMapper,
            WikimediaLicensePolicy licensePolicy,
            StorageProperties storageProperties) {
        this.objectMapper = objectMapper;
        this.licensePolicy = licensePolicy;
        this.storageProperties = storageProperties;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public WikimediaHintImage findExactImage(String fileTitle) {
        String normalizedTitle = normalizeTitle(fileTitle);
        URI requestUri = UriComponentsBuilder.fromUriString(API_URL)
                .queryParam("action", "query")
                .queryParam("titles", normalizedTitle)
                .queryParam("prop", "imageinfo")
                .queryParam("iiprop", "url|size|mime|sha1|extmetadata")
                .queryParam("iilimit", "1")
                .queryParam("iiurlwidth", THUMBNAIL_WIDTH)
                .queryParam("iiextmetadatalanguage", "en")
                .queryParam("iiextmetadatafilter",
                        "License|LicenseShortName|LicenseUrl|Artist|Credit|ImageDescription|UsageTerms")
                .queryParam("format", "json")
                .queryParam("formatversion", "2")
                .queryParam("redirects", "1")
                .queryParam("maxlag", "5")
                .build()
                .encode()
                .toUri();

        JsonNode payload = executeMetadataRequest(requestUri);
        JsonNode pages = payload.path("query").path("pages");
        if (!pages.isArray() || pages.isEmpty()) {
            throw upstreamFailure("Wikimedia Commons returned no file information");
        }

        JsonNode page = pages.get(0);
        if (page.path("missing").asBoolean(false) || page.path("imageinfo").isMissingNode()
                || page.path("imageinfo").isEmpty()) {
            throw new WikimediaImageException(
                    HttpStatus.NOT_FOUND,
                    "Wikimedia Commons file was not found: " + normalizedTitle);
        }

        return parseImage(page, page.path("imageinfo").get(0));
    }

    public DownloadedWikimediaImage download(WikimediaHintImage image) {
        return download(image.sourceBytes() > 0
                && image.sourceBytes() <= storageProperties.getMaxHintImageSize()
                ? image.originalUrl()
                : image.thumbnailUrl());
    }

    public DownloadedWikimediaImage downloadThumbnail(WikimediaHintImage image) {
        return download(image.thumbnailUrl());
    }

    private DownloadedWikimediaImage download(String selectedUrl) {
        URI current = trustedImageUri(selectedUrl);

        try {
            for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
                HttpRequest request = HttpRequest.newBuilder(current)
                        .GET()
                        .timeout(Duration.ofSeconds(30))
                        .header("Accept", "image/avif,image/webp,image/png,image/jpeg")
                        .header("User-Agent", USER_AGENT)
                        .build();
                HttpResponse<InputStream> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    if (response.statusCode() >= 300 && response.statusCode() < 400) {
                        String location = response.headers().firstValue("Location")
                                .orElseThrow(() -> upstreamFailure(
                                        "Wikimedia image redirect did not include a destination"));
                        current = trustedImageUri(current.resolve(location).toString());
                        continue;
                    }
                    if (response.statusCode() != 200 || body == null) {
                        throw upstreamFailure(
                                "Wikimedia image download failed with HTTP " + response.statusCode());
                    }

                    long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                    long maxBytes = storageProperties.getMaxHintImageSize();
                    if (contentLength > maxBytes) {
                        throw new WikimediaImageException(
                                HttpStatus.PAYLOAD_TOO_LARGE,
                                "Wikimedia image exceeds the " + maxBytes + " byte limit");
                    }

                    String contentType = normalizeContentType(
                            response.headers().firstValue("Content-Type").orElse(null));
                    if (!ALLOWED_MIME_TYPES.contains(contentType)) {
                        throw new WikimediaImageException(
                                HttpStatus.UNPROCESSABLE_ENTITY,
                                "Unsupported Wikimedia image type: " + contentType);
                    }

                    byte[] bytes = readBounded(body, maxBytes);
                    return new DownloadedWikimediaImage(bytes, contentType, extension(contentType));
                }
            }
            throw upstreamFailure("Wikimedia image redirected too many times");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw upstreamFailure("Wikimedia image download was interrupted", exception);
        } catch (IOException exception) {
            throw upstreamFailure("Wikimedia image download failed", exception);
        }
    }

    private WikimediaHintImage parseImage(JsonNode page, JsonNode info) {
        String title = page.path("title").asText("");
        String mimeType = info.path("mime").asText("").toLowerCase(Locale.ROOT);
        int width = info.path("width").asInt(0);
        int height = info.path("height").asInt(0);
        String thumbnailUrl = info.path("thumburl").asText("");
        String originalUrl = info.path("url").asText("");
        String sourceSha1 = info.path("sha1").asText("").toLowerCase(Locale.ROOT);
        String sourcePageUrl = info.path("descriptionurl").asText("");
        String licenseLabel = plainText(metadata(info, "LicenseShortName"), 100);
        String licenseCode = plainText(metadata(info, "License"), 100);
        String licenseUrl = canonicalLicenseUrl(
                plainText(metadata(info, "LicenseUrl"), 500), licenseLabel, licenseCode);
        String usageTerms = plainText(metadata(info, "UsageTerms"), 200);
        String authorName = plainText(metadata(info, "Artist"), 300);

        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw invalidImage("Unsupported Wikimedia image type: " + mimeType);
        }
        if (width < MINIMUM_WIDTH || height < MINIMUM_HEIGHT) {
            throw invalidImage("Wikimedia image is too small; minimum dimensions are "
                    + MINIMUM_WIDTH + "x" + MINIMUM_HEIGHT);
        }
        if (!sourceSha1.matches("[a-z0-9]{16,64}")) {
            throw upstreamFailure("Wikimedia Commons returned an invalid image revision hash");
        }
        trustedImageUri(thumbnailUrl);
        trustedImageUri(originalUrl);
        if (!isTrustedCommonsPage(sourcePageUrl)) {
            throw invalidImage("Wikimedia Commons returned an invalid source page URL");
        }
        if (authorName.isBlank()) {
            throw invalidImage("Wikimedia image has no usable author attribution");
        }
        if (!licensePolicy.isAllowed(licenseLabel, licenseUrl, licenseCode)) {
            String displayedLicense = licenseLabel.isBlank() ? "unknown" : licenseLabel;
            throw invalidImage("Wikimedia image license is not allowed: " + displayedLicense
                    + (usageTerms.isBlank() ? "" : " (" + usageTerms + ")"));
        }

        return new WikimediaHintImage(
                title,
                sourceSha1,
                thumbnailUrl,
                originalUrl,
                sourcePageUrl,
                authorName,
                "Image by " + authorName + " via Wikimedia Commons.",
                licenseLabel,
                licenseUrl,
                mimeType,
                info.path("size").asLong(0),
                width,
                height);
    }

    private JsonNode executeMetadataRequest(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build();
        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw upstreamFailure(
                        "Wikimedia Commons request failed with HTTP " + response.statusCode());
            }
            JsonNode payload = objectMapper.readTree(response.body());
            JsonNode apiError = payload.path("error");
            if (!apiError.isMissingNode() && !apiError.isNull()) {
                String code = apiError.path("code").asText("unknown");
                String details = apiError.path("info").asText("No details returned");
                throw upstreamFailure(
                        "Wikimedia Commons returned API error " + code + ": " + details);
            }
            return payload;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw upstreamFailure("Wikimedia Commons request was interrupted", exception);
        } catch (IOException exception) {
            throw upstreamFailure("Wikimedia Commons request failed", exception);
        }
    }

    private static String normalizeTitle(String fileTitle) {
        String normalized = fileTitle == null ? "" : fileTitle.trim();
        if (normalized.regionMatches(true, 0, "File:", 0, "File:".length())) {
            normalized = normalized.substring("File:".length()).trim();
        }
        if (normalized.isBlank()) {
            throw new WikimediaImageException(
                    HttpStatus.BAD_REQUEST, "Wikimedia Commons file name is required");
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            throw new WikimediaImageException(
                    HttpStatus.BAD_REQUEST,
                    "Enter a Wikimedia Commons file name, not a URL");
        }
        return "File:" + normalized;
    }

    private static String metadata(JsonNode info, String name) {
        return info.path("extmetadata").path(name).path("value").asText("");
    }

    private static String plainText(String html, int maxLength) {
        String value = Jsoup.parseBodyFragment(html == null ? "" : html).text().trim();
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 3) + "...";
    }

    private static URI trustedImageUri(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || host == null
                    || !ALLOWED_IMAGE_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
                throw invalidImage("Wikimedia Commons returned an untrusted image URL");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw invalidImage("Wikimedia Commons returned an invalid image URL");
        }
    }

    private static boolean isTrustedCommonsPage(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "commons.wikimedia.org".equalsIgnoreCase(uri.getHost());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String canonicalLicenseUrl(String value, String label, String code) {
        if (value != null && value.startsWith("//")) {
            value = "https:" + value;
        }
        if ("pd".equalsIgnoreCase(code) && "Public domain".equalsIgnoreCase(label)) {
            return COMMONS_COPYRIGHT_TAGS_URL;
        }
        return value;
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int separator = contentType.indexOf(';');
        return (separator < 0 ? contentType : contentType.substring(0, separator))
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static byte[] readBounded(InputStream input, long maxBytes) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new WikimediaImageException(
                            HttpStatus.PAYLOAD_TOO_LARGE,
                            "Wikimedia image exceeds the " + maxBytes + " byte limit");
                }
                output.write(buffer, 0, read);
            }
            if (total == 0) {
                throw upstreamFailure("Wikimedia Commons returned an empty image");
            }
            return output.toByteArray();
        }
    }

    private static String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }

    private static WikimediaImageException invalidImage(String message) {
        return new WikimediaImageException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    private static WikimediaImageException upstreamFailure(String message) {
        return new WikimediaImageException(HttpStatus.BAD_GATEWAY, message);
    }

    private static WikimediaImageException upstreamFailure(String message, Throwable cause) {
        return new WikimediaImageException(HttpStatus.BAD_GATEWAY, message, cause);
    }
}
