package io.clueterra.budget.source.wikimedia;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.budget.config.ExternalHttpProperties;
import io.clueterra.budget.config.WikimediaProperties;
import io.clueterra.budget.exception.ExternalSourceException;
import io.clueterra.budget.exception.MediaWikiMaxLagException;
import io.clueterra.budget.image.DownloadedBudgetImage;
import io.clueterra.budget.image.WikimediaImageCandidate;

import lombok.RequiredArgsConstructor;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Component
@RequiredArgsConstructor
public class WikimediaCommonsClient {

    private static final String COMMONS_COPYRIGHT_TAGS_URL =
            "https://commons.wikimedia.org/wiki/Commons:Copyright_tags";
    private static final Set<String> ALLOWED_MIME_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> ALLOWED_IMAGE_HOSTS =
            Set.of("upload.wikimedia.org", "thumb.wikimedia.org");

    private final OkHttpClient externalSourceHttpClient;
    private final ObjectMapper objectMapper;
    private final WikimediaProperties properties;
    private final ExternalHttpProperties httpProperties;
    private final WikimediaLicensePolicy licensePolicy;

    public Optional<WikimediaImageCandidate> findExactImage(
            String fileTitle, String authorNameOverride, String creditOverride) {
        if (!properties.enabled() || fileTitle == null || fileTitle.isBlank()) {
            return Optional.empty();
        }
        String normalizedTitle = fileTitle.startsWith("File:") ? fileTitle : "File:" + fileTitle;
        return findExactImageBatch(
                        List.of(normalizedTitle), authorNameOverride, creditOverride)
                .stream()
                .findFirst();
    }

    public List<WikimediaImageCandidate> findExactImages(Collection<String> fileTitles) {
        if (!properties.enabled() || fileTitles == null || fileTitles.isEmpty()) {
            return List.of();
        }
        List<String> normalizedTitles =
                fileTitles.stream()
                        .filter(title -> title != null && !title.isBlank())
                        .map(title -> title.startsWith("File:") ? title : "File:" + title)
                        .collect(Collectors.toCollection(LinkedHashSet::new))
                        .stream()
                        .toList();
        List<WikimediaImageCandidate> candidates = new ArrayList<>();
        for (int from = 0; from < normalizedTitles.size(); from += properties.metadataBatchSize()) {
            int to = Math.min(from + properties.metadataBatchSize(), normalizedTitles.size());
            candidates.addAll(
                    findExactImageBatch(normalizedTitles.subList(from, to), null, null));
            if (to < normalizedTitles.size()) {
                pauseBetweenRequests();
            }
        }
        return List.copyOf(candidates);
    }

    private List<WikimediaImageCandidate> findExactImageBatch(
            List<String> normalizedTitles, String authorNameOverride, String creditOverride) {
        HttpUrl baseUrl = trustedCommonsApiUrl();
        HttpUrl url =
                baseUrl
                        .newBuilder()
                        .addQueryParameter("action", "query")
                        .addQueryParameter("titles", String.join("|", normalizedTitles))
                        .addQueryParameter("prop", "imageinfo")
                        .addQueryParameter("iiprop", "url|size|mime|sha1|extmetadata")
                        .addQueryParameter("iilimit", "1")
                        .addQueryParameter("iiurlwidth", String.valueOf(properties.thumbnailWidth()))
                        .addQueryParameter("iiextmetadatalanguage", "en")
                        .addQueryParameter(
                                "iiextmetadatafilter",
                                "License|LicenseShortName|LicenseUrl|Artist|Credit|ImageDescription")
                        .addQueryParameter("format", "json")
                        .addQueryParameter("formatversion", "2")
                        .addQueryParameter("redirects", "1")
                        .addQueryParameter("maxlag", String.valueOf(properties.maxLagSeconds()))
                        .build();

        JsonNode pages = executeJson(url).path("query").path("pages");
        if (!pages.isArray() || pages.isEmpty()) {
            return List.of();
        }
        return StreamSupport.stream(pages.spliterator(), false)
                .map(page -> parseCandidate(page, authorNameOverride, creditOverride))
                .flatMap(Optional::stream)
                .toList();
    }

    public DownloadedBudgetImage download(WikimediaImageCandidate candidate) {
        String selectedUrl =
                candidate.sourceBytes() > 0 && candidate.sourceBytes() <= properties.maximumDownloadBytes()
                        ? candidate.originalUrl()
                        : candidate.thumbnailUrl();
        HttpUrl url = HttpUrl.parse(selectedUrl);
        if (isUntrustedImageUrl(url)) {
            throw new ExternalSourceException("Rejected Wikimedia image URL");
        }
        Request request =
                new Request.Builder()
                        .url(url)
                        .header("Accept", "image/avif,image/webp,image/png,image/jpeg")
                        .header("User-Agent", httpProperties.userAgent())
                        .get()
                        .build();
        try (Response response = externalSourceHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || isUntrustedImageUrl(response.request().url())) {
                throw new ExternalSourceException(
                        "Wikimedia image download failed with HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new ExternalSourceException("Wikimedia returned an empty image");
            }
            String contentType =
                    normalizeContentType(
                            body.contentType() == null
                                    ? null
                                    : Objects.requireNonNull(body.contentType()).toString());
            if (!ALLOWED_MIME_TYPES.contains(contentType)) {
                throw new ExternalSourceException("Unsupported Wikimedia image type: " + contentType);
            }
            byte[] bytes = readBounded(body.byteStream(), properties.maximumDownloadBytes());
            return new DownloadedBudgetImage(bytes, contentType, extension(contentType));
        } catch (IOException exception) {
            throw new ExternalSourceException("Wikimedia image download failed", exception);
        }
    }

    private Optional<WikimediaImageCandidate> parseCandidate(
            JsonNode page, String authorNameOverride, String creditOverride) {
        JsonNode info = page.path("imageinfo").path(0);
        if (page.path("missing").asBoolean(false) || info.isMissingNode()) {
            return Optional.empty();
        }
        String mimeType = info.path("mime").asText("");
        int width = info.path("width").asInt(0);
        int height = info.path("height").asInt(0);
        String thumbnailUrl = info.path("thumburl").asText("");
        String originalUrl = info.path("url").asText("");
        String licenseLabel = metadata(info, "LicenseShortName");
        String licenseCode = metadata(info, "License");
        String licenseUrl =
                canonicalLicenseUrl(metadata(info, "LicenseUrl"), licenseLabel, licenseCode);
        String author = preferredAttribution(authorNameOverride, metadata(info, "Artist"), 300);
        String credit = preferredAttribution(creditOverride, metadata(info, "Credit"), 500);
        String description = plainText(metadata(info, "ImageDescription"), 600);
        String sourcePageUrl = info.path("descriptionurl").asText("");

        if (!ALLOWED_MIME_TYPES.contains(mimeType)
                || !meetsMinimumDimensions(width, height)
                || isUntrustedImageUrl(thumbnailUrl)
                || isUntrustedImageUrl(originalUrl)
                || !isTrustedCommonsPage(sourcePageUrl)
                || author.isBlank()
                || !licensePolicy.isAllowed(licenseLabel, licenseUrl, licenseCode)) {
            return Optional.empty();
        }

        return Optional.of(
                new WikimediaImageCandidate(
                        page.path("pageid").asLong(),
                        info.path("sha1").asText("unknown"),
                        page.path("title").asText(""),
                        thumbnailUrl,
                        originalUrl,
                        sourcePageUrl,
                        author,
                        credit,
                        licenseLabel,
                        licenseUrl,
                        description,
                        mimeType,
                        info.path("size").asLong(0),
                        width,
                        height));
    }

    private boolean meetsMinimumDimensions(int width, int height) {
        return (width >= properties.minimumWidth() && height >= properties.minimumHeight())
                || (width >= properties.minimumHeight() && height >= properties.minimumWidth());
    }

    private HttpUrl trustedCommonsApiUrl() {
        HttpUrl baseUrl = HttpUrl.parse(properties.apiUrl());
        if (baseUrl == null
                || !baseUrl.isHttps()
                || !"commons.wikimedia.org".equalsIgnoreCase(baseUrl.host())) {
            throw new ExternalSourceException("Invalid Wikimedia Commons API URL");
        }
        return baseUrl;
    }

    private JsonNode executeJson(HttpUrl url) {
        Request request =
                new Request.Builder()
                        .url(url)
                        .header("Accept", "application/json")
                        .header("User-Agent", httpProperties.userAgent())
                        .get()
                        .build();
        try (Response response = externalSourceHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new ExternalSourceException(
                        "Wikimedia Commons request failed with HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new ExternalSourceException("Wikimedia Commons returned an empty response");
            }
            JsonNode payload = objectMapper.readTree(body.charStream());
            JsonNode apiError = payload.path("error");
            if (!apiError.isMissingNode() && !apiError.isNull()) {
                throw mediaWikiApiError(response, apiError);
            }
            return payload;
        } catch (IOException exception) {
            throw new ExternalSourceException("Wikimedia Commons request failed", exception);
        }
    }

    private ExternalSourceException mediaWikiApiError(Response response, JsonNode apiError) {
        String code = apiError.path("code").asText("unknown");
        String details = apiError.path("info").asText("No error details returned");
        if ("maxlag".equalsIgnoreCase(code)) {
            return new MediaWikiMaxLagException(
                    "Wikimedia Commons request", details, maxLagDelay(response, apiError));
        }
        return new ExternalSourceException(
                "Wikimedia Commons request returned API error " + code + ": " + details);
    }

    private Duration maxLagDelay(Response response, JsonNode apiError) {
        long retryAfterSeconds = positiveLong(response.header("Retry-After"));
        long reportedLagSeconds = Math.max(0L, (long) Math.ceil(apiError.path("lag").asDouble(0)));
        return Duration.ofSeconds(Math.max(1L, Math.max(retryAfterSeconds, reportedLagSeconds)));
    }

    private long positiveLong(String value) {
        try {
            return Math.max(0L, Long.parseLong(Objects.requireNonNullElse(value, "0")));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private byte[] readBounded(InputStream input, long maxBytes) throws IOException {
        try (InputStream stream = input;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = stream.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new ExternalSourceException("Wikimedia image exceeds the configured size limit");
                }
                output.write(buffer, 0, read);
            }
            if (total == 0) {
                throw new ExternalSourceException("Wikimedia returned an empty image");
            }
            return output.toByteArray();
        }
    }

    private String metadata(JsonNode info, String name) {
        return info.path("extmetadata").path(name).path("value").asText("");
    }

    private String plainText(String html, int maxLength) {
        String value = Jsoup.parseBodyFragment(html == null ? "" : html).text().trim();
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 3) + "...";
    }

    private String preferredAttribution(String override, String metadata, int maxLength) {
        String value = override == null || override.isBlank() ? metadata : override;
        return plainText(value, maxLength);
    }

    private boolean isTrustedCommonsPage(String value) {
        HttpUrl url = HttpUrl.parse(value);
        return url != null && url.isHttps() && "commons.wikimedia.org".equalsIgnoreCase(url.host());
    }

    private boolean isUntrustedImageUrl(String value) {
        return isUntrustedImageUrl(HttpUrl.parse(value));
    }

    private boolean isUntrustedImageUrl(HttpUrl url) {
        return url == null
                || !url.isHttps()
                || !ALLOWED_IMAGE_HOSTS.contains(url.host().toLowerCase(Locale.ROOT));
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int separator = contentType.indexOf(';');
        return (separator < 0 ? contentType : contentType.substring(0, separator))
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String canonicalLicenseUrl(String value, String label, String code) {
        HttpUrl url = HttpUrl.parse(value);
        if (url != null
                && ("creativecommons.org".equalsIgnoreCase(url.host())
                || "commons.wikimedia.org".equalsIgnoreCase(url.host()))) {
            return url.newBuilder().scheme("https").build().toString();
        }
        if ("pd".equalsIgnoreCase(code) && "Public domain".equalsIgnoreCase(label)) {
            return COMMONS_COPYRIGHT_TAGS_URL;
        }
        return value;
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }

    private void pauseBetweenRequests() {
        if (!properties.requestDelay().isZero() && !Thread.currentThread().isInterrupted()) {
            LockSupport.parkNanos(properties.requestDelay().toNanos());
        }
    }
}
