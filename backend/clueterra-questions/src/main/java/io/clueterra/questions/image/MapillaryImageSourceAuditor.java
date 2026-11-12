package io.clueterra.questions.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.clients.common.HttpClientHelper;
import io.clueterra.questions.clients.common.dto.SourceProperties;
import io.clueterra.questions.config.SourcesProperties;
import io.clueterra.questions.entity.enums.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MapillaryImageSourceAuditor {

    private static final Pattern PKEY_PATTERN = Pattern.compile("(?:[?&]pKey=|^)([0-9]+)");
    private static final String LICENSE_LABEL = "CC BY-SA 4.0";
    private static final String LICENSE_URL = "https://creativecommons.org/licenses/by-sa/4.0/";

    private final HttpClientHelper httpClient;
    private final ObjectMapper objectMapper;
    private final SourcesProperties sourcesProperties;

    public boolean supports(ImageAuditCandidate candidate) {
        return "MAPILLARY".equalsIgnoreCase(candidate.provider())
                || "MAPILLARY".equalsIgnoreCase(candidate.source());
    }

    public ImageSourceValidation validate(ImageAuditCandidate candidate) {
        SourceProperties properties = sourcesProperties.getSource(DataSource.MAPILLARY);
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getApiKey())) {
            return ImageSourceValidation.of(
                    ImageSourceValidationStatus.UNSUPPORTED,
                    "Mapillary validation is disabled or has no API key");
        }
        String imageId = extractImageId(candidate);
        if (!StringUtils.hasText(imageId)) {
            return ImageSourceValidation.of(
                    ImageSourceValidationStatus.FETCH_FAILED,
                    "Could not extract a Mapillary image id from the canonical source key");
        }

        String url = UriComponentsBuilder.fromUriString(properties.getBaseUrl() + "/" + imageId)
                .queryParam("fields", "id,thumb_original_url,thumb_2048_url,width,height,creator")
                .queryParam("access_token", properties.getApiKey())
                .build()
                .encode()
                .toUriString();
        try {
            String response = httpClient.get(
                    DataSource.MAPILLARY,
                    url,
                    Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())));
            JsonNode root = objectMapper.readTree(response);
            if (!root.path("error").isMissingNode()) {
                return mapApiError(root.path("error"));
            }
            String originalUrl = root.path("thumb_original_url").asText(null);
            String thumbnailUrl = root.path("thumb_2048_url").asText(null);
            String imageUrl = StringUtils.hasText(originalUrl) ? originalUrl : thumbnailUrl;
            if (!StringUtils.hasText(imageUrl)) {
                return ImageSourceValidation.of(
                        ImageSourceValidationStatus.SOURCE_MISSING,
                        "Mapillary image no longer exposes a downloadable image");
            }
            String username = root.path("creator").path("username").asText(null);
            String sourcePageUrl = "https://www.mapillary.com/app/?pKey=" + imageId + "&focus=photo";
            String attribution = StringUtils.hasText(username)
                    ? "Mapillary image by @" + username + "."
                    : "Street-level imagery via Mapillary.";
            return ImageSourceValidation.available(new ImageMetadata(
                    imageUrl,
                    sourcePageUrl,
                    username,
                    attribution,
                    LICENSE_LABEL,
                    LICENSE_URL,
                    imageUrl,
                    null,
                    "image/jpeg",
                    root.path("width").isInt() ? root.path("width").asInt() : null,
                    root.path("height").isInt() ? root.path("height").asInt() : null));
        } catch (Exception exception) {
            return ImageSourceValidation.of(
                    ImageSourceValidationStatus.FETCH_FAILED,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private ImageSourceValidation mapApiError(JsonNode error) {
        int code = error.path("code").asInt(0);
        String message = error.path("message").asText("Mapillary API returned an error");
        return code == 803
                ? ImageSourceValidation.of(ImageSourceValidationStatus.SOURCE_MISSING, message)
                : ImageSourceValidation.of(ImageSourceValidationStatus.FETCH_FAILED, message);
    }

    private String extractImageId(ImageAuditCandidate candidate) {
        String value = StringUtils.hasText(candidate.sourceKey())
                ? candidate.sourceKey()
                : candidate.imageSourcePageUrl();
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Matcher matcher = PKEY_PATTERN.matcher(value);
        return matcher.find() ? matcher.group(1) : value.matches("[0-9]+") ? value : null;
    }
}
