package io.clueterra.questions.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.clients.common.ImageRightsSupport;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.storage.ImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class WikimediaImageSourceAuditor {

    private static final String COMMONS_API_BASE = "https://commons.wikimedia.org/w/api.php";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final HttpClientHelper httpClient;
    private final ObjectMapper objectMapper;

    public boolean supports(ImageAuditCandidate candidate) {
        return isWikimediaUrl(candidate.originalImageUrl())
                || isCommonsFilePage(candidate.imageSourcePageUrl())
                || isWikimediaUrl(candidate.recoverySourceUrl());
    }

    public ImageSourceValidation validate(ImageAuditCandidate candidate) {
        String filename = firstFilename(
                candidate.imageSourcePageUrl(),
                candidate.originalImageUrl(),
                candidate.recoverySourceUrl());
        if (!StringUtils.hasText(filename)) {
            return ImageSourceValidation.of(
                    ImageSourceValidationStatus.FETCH_FAILED,
                    "Could not extract a Wikimedia Commons file title from the stored source URLs");
        }

        try {
            String apiUrl = COMMONS_API_BASE
                    + "?action=query&format=json&formatversion=2"
                    + "&redirects=1&titles=" + encode("File:" + filename)
                    + "&prop=imageinfo&iiprop=" + encode("url|size|mime|sha1|extmetadata")
                    + "&iiurlwidth=2048";
            String body = httpClient.get(DataSource.WIKIMEDIA_COMMONS, apiUrl, TIMEOUT);
            return parse(body);
        } catch (Exception e) {
            return ImageSourceValidation.of(
                    ImageSourceValidationStatus.FETCH_FAILED,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private ImageSourceValidation parse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode apiError = root.path("error");
        if (!apiError.isMissingNode()) {
            String reason = ImageRightsSupport.firstNonBlank(
                    apiError.path("info").asText(null),
                    apiError.path("code").asText(null),
                    "Commons API returned an error");
            return ImageSourceValidation.of(ImageSourceValidationStatus.FETCH_FAILED, reason);
        }

        JsonNode pages = root.path("query").path("pages");
        if (!pages.isArray() || pages.isEmpty()) {
            return ImageSourceValidation.of(ImageSourceValidationStatus.SOURCE_MISSING, "Commons returned no file page");
        }

        JsonNode page = pages.get(0);
        if (page.path("missing").asBoolean(false)) {
            return ImageSourceValidation.of(ImageSourceValidationStatus.SOURCE_MISSING, "Commons file page no longer exists");
        }

        JsonNode imageInfo = page.path("imageinfo");
        if (!imageInfo.isArray() || imageInfo.isEmpty()) {
            return ImageSourceValidation.of(ImageSourceValidationStatus.SOURCE_MISSING, "Commons file has no downloadable revision");
        }

        JsonNode info = imageInfo.get(0);
        JsonNode extMetadata = info.path("extmetadata");
        String rawArtist = extValue(extMetadata, "Artist");
        String rawCredit = extValue(extMetadata, "Credit");
        String authorName = ImageRightsSupport.wikimediaAuthorName(rawArtist, rawCredit);
        String licenseUrl = ImageRightsSupport.licenseUrl(extValue(extMetadata, "LicenseUrl"));
        String licenseLabel = ImageRightsSupport.firstNonBlank(
                ImageRightsSupport.cleanText(extValue(extMetadata, "LicenseShortName")),
                ImageRightsSupport.licenseLabel(licenseUrl),
                ImageRightsSupport.cleanText(extValue(extMetadata, "UsageTerms")));
        String usageTerms = extValue(extMetadata, "UsageTerms");
        String sourcePageUrl = ImageRightsSupport.firstNonBlank(
                info.path("descriptionurl").asText(null),
                info.path("descriptionshorturl").asText(null));

        ImageMetadata metadata = new ImageMetadata(
                canonicalOriginalImageUrl(info.path("url").asText(null)),
                sourcePageUrl,
                authorName,
                ImageRightsSupport.wikimediaAttributionText(authorName, rawCredit),
                licenseLabel,
                licenseUrl,
                canonicalOriginalImageUrl(ImageRightsSupport.firstNonBlank(
                        info.path("thumburl").asText(null),
                        info.path("url").asText(null))),
                info.path("sha1").asText(null),
                info.path("mime").asText(null),
                positiveInt(info.path("width").asInt(0)),
                positiveInt(info.path("height").asInt(0)));

        if (!StringUtils.hasText(metadata.originalImageUrl())
                || !StringUtils.hasText(sourcePageUrl)
                || !StringUtils.hasText(licenseLabel)) {
            return new ImageSourceValidation(
                    ImageSourceValidationStatus.FETCH_FAILED,
                    metadata,
                    "Commons file exists but required source or license metadata is incomplete");
        }
        if (!ImageRightsSupport.isCommerciallySafeOpenLicense(licenseLabel, licenseUrl, usageTerms)) {
            return new ImageSourceValidation(
                    ImageSourceValidationStatus.RESTRICTED_LICENSE,
                    metadata,
                    "Current Commons metadata no longer exposes a commercially safe open license");
        }
        return ImageSourceValidation.available(metadata);
    }

    private String firstFilename(String... urls) {
        for (String url : urls) {
            String filename = extractFilename(url);
            if (StringUtils.hasText(filename)) {
                return filename;
            }
        }
        return null;
    }

    private String extractFilename(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        String path = url;
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        int fragmentIndex = path.indexOf('#');
        if (fragmentIndex >= 0) {
            path = path.substring(0, fragmentIndex);
        }
        int filePageIndex = path.indexOf("/wiki/File:");
        if (filePageIndex >= 0) {
            return decode(path.substring(filePageIndex + "/wiki/File:".length()));
        }
        if (!isWikimediaUrl(path)) {
            return null;
        }
        String lowerPath = path.toLowerCase();
        int thumbnailIndex = lowerPath.indexOf("/thumb/");
        if (thumbnailIndex >= 0) {
            int finalSlash = path.lastIndexOf('/');
            int originalFileSlash = finalSlash > thumbnailIndex
                    ? path.lastIndexOf('/', finalSlash - 1)
                    : -1;
            if (originalFileSlash >= 0 && originalFileSlash < finalSlash) {
                return decode(path.substring(originalFileSlash + 1, finalSlash));
            }
        }
        int slashIndex = path.lastIndexOf('/');
        return slashIndex >= 0 && slashIndex < path.length() - 1
                ? decode(path.substring(slashIndex + 1))
                : null;
    }

    private boolean isWikimediaUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String lower = value.toLowerCase();
        return lower.startsWith("https://upload.wikimedia.org/")
                || lower.contains("commons.wikimedia.org/wiki/file:");
    }

    private boolean isCommonsFilePage(String value) {
        return StringUtils.hasText(value)
                && value.toLowerCase().contains("commons.wikimedia.org/wiki/file:");
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8).replace('_', ' ');
        } catch (IllegalArgumentException e) {
            return value.replace('_', ' ');
        }
    }

    private static String extValue(JsonNode extMetadata, String field) {
        return extMetadata.path(field).path("value").asText(null);
    }

    private static String canonicalOriginalImageUrl(String value) {
        return StringUtils.hasText(value)
                ? ImageStorageService.normalizeDownloadUri(value).toString()
                : value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static Integer positiveInt(int value) {
        return value > 0 ? value : null;
    }
}
