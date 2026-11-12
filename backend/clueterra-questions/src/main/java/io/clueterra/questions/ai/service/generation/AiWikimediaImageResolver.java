package io.clueterra.questions.ai.service.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.clients.common.ImageRightsSupport;
import io.clueterra.questions.clients.common.dto.SourceProperties;
import io.clueterra.questions.config.SourcesProperties;
import io.clueterra.questions.entity.enums.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class AiWikimediaImageResolver {

    private static final String COMMONS_API_BASE = "https://commons.wikimedia.org/w/api.php";
    private static final String IMAGE_INFO_PROPS = "url|extmetadata";
    private final HttpClientHelper httpClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    public AiWikimediaImageResolver(
            HttpClientHelper httpClient,
            ObjectMapper objectMapper,
            SourcesProperties sourcesProperties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        SourceProperties properties = sourcesProperties.getSource(DataSource.WIKIMEDIA_COMMONS);
        this.timeout = Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds()));
        httpClient.configureRateLimiter(
                DataSource.WIKIMEDIA_COMMONS,
                Math.max(1, properties.getRateLimitPerSecond()));
    }

    public Optional<AiResolvedWikimediaImage> resolveImage(String imageUrl, List<String> searchTerms) {
        Optional<AiResolvedWikimediaImage> exact = resolveExact(imageUrl);
        if (exact.isPresent()) {
            return exact;
        }

        String query = searchQuery(imageUrl, searchTerms);
        return search(query);
    }

    public Optional<AiResolvedWikimediaImage> search(String query) {
        return searchCandidates(query).stream().findFirst();
    }

    public Optional<AiResolvedWikimediaImage> searchPublicDomain(String query) {
        return searchCandidates(query).stream()
                .filter(image -> ImageRightsSupport.isPublicDomainOrCc0License(
                        image.licenseLabel(), image.licenseUrl()))
                .findFirst();
    }

    private List<AiResolvedWikimediaImage> searchCandidates(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        try {
            String apiUrl = COMMONS_API_BASE
                    + "?action=query&format=json&formatversion=2"
                    + "&generator=search"
                    + "&gsrnamespace=6"
                    + "&gsrlimit=5"
                    + "&gsrsearch=" + encode(query)
                    + "&prop=imageinfo"
                    + "&iiprop=" + encode(IMAGE_INFO_PROPS);

            String body = httpClient.get(DataSource.WIKIMEDIA_COMMONS, apiUrl, timeout);
            return parsePages(body);
        } catch (Exception e) {
            log.warn("Failed to search Wikimedia image for {}", query, e);
            return List.of();
        }
    }

    private Optional<AiResolvedWikimediaImage> resolveExact(String imageUrl) {
        String filename = extractFilename(imageUrl);
        if (!StringUtils.hasText(filename)) {
            return Optional.empty();
        }

        try {
            String apiUrl = COMMONS_API_BASE
                    + "?action=query&format=json&formatversion=2"
                    + "&titles=" + encode("File:" + filename)
                    + "&prop=imageinfo"
                    + "&iiprop=" + encode(IMAGE_INFO_PROPS);

            String body = httpClient.get(DataSource.WIKIMEDIA_COMMONS, apiUrl, timeout);
            return parsePages(body).stream().findFirst();
        } catch (Exception e) {
            log.warn("Failed to resolve Wikimedia image {}", filename, e);
            return Optional.empty();
        }
    }

    private List<AiResolvedWikimediaImage> parsePages(String body) throws Exception {
        JsonNode pages = objectMapper.readTree(body).path("query").path("pages");
        if (!pages.isArray() || pages.isEmpty()) {
            return List.of();
        }

        List<AiResolvedWikimediaImage> images = new ArrayList<>();
        for (JsonNode page : pages) {
            parsePage(page).ifPresent(images::add);
        }
        return images;
    }

    private Optional<AiResolvedWikimediaImage> parsePage(JsonNode page) {
        if (page.path("missing").asBoolean(false)) {
            return Optional.empty();
        }

        JsonNode imageInfo = page.path("imageinfo");
        if (!imageInfo.isArray() || imageInfo.isEmpty()) {
            return Optional.empty();
        }

        JsonNode info = imageInfo.get(0);
        String imageUrl = info.path("url").asText(null);
        if (!isSupportedImageUrl(imageUrl)) {
            return Optional.empty();
        }
        imageUrl = stripQuery(imageUrl);

        JsonNode extMetadata = info.path("extmetadata");
        String licenseUrl = ImageRightsSupport.licenseUrl(extValue(extMetadata, "LicenseUrl"));
        String licenseLabel = ImageRightsSupport.firstNonBlank(
                ImageRightsSupport.cleanText(extValue(extMetadata, "LicenseShortName")),
                ImageRightsSupport.licenseLabel(licenseUrl),
                ImageRightsSupport.cleanText(extValue(extMetadata, "UsageTerms")));
        if (!ImageRightsSupport.isCommerciallySafeOpenLicense(licenseLabel, licenseUrl, extValue(extMetadata, "UsageTerms"))) {
            return Optional.empty();
        }

        String rawArtist = extValue(extMetadata, "Artist");
        String rawCredit = extValue(extMetadata, "Credit");
        String authorName = ImageRightsSupport.wikimediaAuthorName(rawArtist, rawCredit);
        return Optional.of(new AiResolvedWikimediaImage(
                page.path("title").asText(null),
                imageUrl,
                ImageRightsSupport.firstNonBlank(
                        info.path("descriptionurl").asText(null),
                        info.path("descriptionshorturl").asText(null)),
                licenseLabel,
                licenseUrl,
                authorName,
                ImageRightsSupport.wikimediaAttributionText(authorName, rawCredit)));
    }

    private static boolean isSupportedImageUrl(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return false;
        }
        // Commons appends utm_* tracking params to imageinfo urls, so the extension is not the url suffix.
        String lower = stripQuery(imageUrl).toLowerCase();
        return lower.startsWith("https://upload.wikimedia.org/")
                && (lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".webp"));
    }

    private static String stripQuery(String url) {
        int cut = url.length();
        int queryIdx = url.indexOf('?');
        if (queryIdx >= 0) {
            cut = queryIdx;
        }
        int fragmentIdx = url.indexOf('#');
        if (fragmentIdx >= 0 && fragmentIdx < cut) {
            cut = fragmentIdx;
        }
        return url.substring(0, cut);
    }

    private static String searchQuery(String imageUrl, List<String> searchTerms) {
        if (searchTerms != null && !searchTerms.isEmpty()) {
            String joined = String.join(" ", searchTerms).trim();
            if (StringUtils.hasText(joined)) {
                return joined;
            }
        }
        return extractFilename(imageUrl);
    }

    private static String extractFilename(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        String path = stripQuery(url);
        if (path.contains("/wiki/File:")) {
            int fileIdx = path.indexOf("/wiki/File:") + "/wiki/File:".length();
            return decodeFilename(path.substring(fileIdx));
        }
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return decodeFilename(path.substring(lastSlash + 1));
        }
        return null;
    }

    private static String decodeFilename(String encoded) {
        try {
            return URLDecoder.decode(encoded, StandardCharsets.UTF_8).replace('_', ' ');
        } catch (Exception e) {
            return encoded.replace('_', ' ');
        }
    }

    private static String extValue(JsonNode extMetadata, String field) {
        return extMetadata.path(field).path("value").asText(null);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
