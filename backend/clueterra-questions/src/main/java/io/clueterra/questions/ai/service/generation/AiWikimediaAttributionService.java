package io.clueterra.questions.ai.service.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.clients.common.ImageRightsSupport;
import io.clueterra.questions.entity.enums.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiWikimediaAttributionService {

    private static final String COMMONS_API_BASE = "https://commons.wikimedia.org/w/api.php";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final HttpClientHelper httpClient;
    private final ObjectMapper objectMapper;

    public Optional<AiWikimediaAttribution> fetchAttribution(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return Optional.empty();
        }

        String filename = extractFilename(imageUrl);
        if (!StringUtils.hasText(filename)) {
            log.debug("Could not extract filename from URL: {}", imageUrl);
            return Optional.empty();
        }

        try {
            String apiUrl = COMMONS_API_BASE
                    + "?action=query&format=json&formatversion=2"
                    + "&titles=" + encode("File:" + filename)
                    + "&prop=imageinfo&iiprop=" + encode("url|extmetadata");

            String body = httpClient.get(DataSource.WIKIMEDIA_COMMONS, apiUrl, TIMEOUT);
            return parseAttribution(body);
        } catch (Exception e) {
            log.debug("Failed to fetch Wikimedia attribution for {}: {}", filename, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<AiWikimediaAttribution> parseAttribution(String body) throws Exception {
        JsonNode pages = objectMapper.readTree(body).path("query").path("pages");
        if (!pages.isArray() || pages.isEmpty()) {
            return Optional.empty();
        }

        JsonNode page = pages.get(0);
        JsonNode imageInfo = page.path("imageinfo");
        if (!imageInfo.isArray() || imageInfo.isEmpty()) {
            return Optional.empty();
        }

        JsonNode info = imageInfo.get(0);
        JsonNode extMetadata = info.path("extmetadata");

        String sourcePageUrl = ImageRightsSupport.firstNonBlank(
                info.path("descriptionurl").asText(null),
                info.path("descriptionshorturl").asText(null));

        String rawArtist = extValue(extMetadata, "Artist");
        String rawCredit = extValue(extMetadata, "Credit");
        String authorName = ImageRightsSupport.wikimediaAuthorName(rawArtist, rawCredit);
        String licenseUrl = ImageRightsSupport.licenseUrl(extValue(extMetadata, "LicenseUrl"));
        String licenseLabel = ImageRightsSupport.firstNonBlank(
                ImageRightsSupport.cleanText(extValue(extMetadata, "LicenseShortName")),
                ImageRightsSupport.licenseLabel(licenseUrl),
                ImageRightsSupport.cleanText(extValue(extMetadata, "UsageTerms")));

        if (!StringUtils.hasText(sourcePageUrl) || !StringUtils.hasText(licenseLabel)) {
            return Optional.empty();
        }

        if (!ImageRightsSupport.isCommerciallySafeOpenLicense(licenseLabel, licenseUrl, extValue(extMetadata, "UsageTerms"))) {
            log.debug("Wikimedia image has restricted license: {}", licenseLabel);
            return Optional.empty();
        }

        String attributionText = ImageRightsSupport.wikimediaAttributionText(authorName, rawCredit);

        return Optional.of(new AiWikimediaAttribution(
                sourcePageUrl,
                authorName,
                attributionText,
                licenseLabel,
                licenseUrl));
    }

    private String extractFilename(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }

        String path = url;
        int queryIdx = path.indexOf('?');
        if (queryIdx > 0) {
            path = path.substring(0, queryIdx);
        }

        if (path.contains("/commons/")) {
            int fileIdx = path.lastIndexOf('/');
            if (fileIdx >= 0 && fileIdx < path.length() - 1) {
                String raw = path.substring(fileIdx + 1);
                return decodeFilename(raw);
            }
        }

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

    private String decodeFilename(String encoded) {
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
