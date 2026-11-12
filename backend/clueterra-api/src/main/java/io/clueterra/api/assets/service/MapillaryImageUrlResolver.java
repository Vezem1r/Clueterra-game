package io.clueterra.api.assets.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MapillaryImageUrlResolver {

    private static final Pattern PKEY_PATTERN = Pattern.compile("[?&]pKey=([^&]+)");
    private static final String DEFAULT_BASE_URL = "https://graph.mapillary.com";
    private static final String IMAGE_FIELDS = "thumb_2048_url";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;

    public MapillaryImageUrlResolver(ObjectMapper objectMapper,
                                     @Value("${clueterra.mapillary.base-url:https://graph.mapillary.com}") String baseUrl,
                                     @Value("${MAPILLARY_CLIENT_TOKEN:}") String apiKey) {
        this.objectMapper = objectMapper;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey;
    }

    public Optional<String> resolveFreshImageUrl(String sourcePageUrl) {
        if (!StringUtils.hasText(sourcePageUrl) || !StringUtils.hasText(apiKey)) {
            return Optional.empty();
        }

        return extractPKey(sourcePageUrl).flatMap(this::fetchThumbUrl);
    }

    private Optional<String> fetchThumbUrl(String pKey) {
        String encodedPKey = URLEncoder.encode(pKey, StandardCharsets.UTF_8);
        String encodedFields = URLEncoder.encode(IMAGE_FIELDS, StandardCharsets.UTF_8);
        String encodedApiKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        URI uri = URI.create(baseUrl + "/" + encodedPKey
                + "?fields=" + encodedFields
                + "&access_token=" + encodedApiKey);

        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .header("User-Agent", "Clueterra/1.0 (support@clueterra.com)")
                    .build();
            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body() == null || response.body().length == 0) {
                log.debug("Failed to refresh Mapillary image URL for {}: HTTP {}", pKey, response.statusCode());
                return Optional.empty();
            }

            JsonNode node = objectMapper.readTree(response.body());
            String thumbUrl = node.path("thumb_2048_url").asText(null);
            return StringUtils.hasText(thumbUrl) ? Optional.of(thumbUrl) : Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Mapillary image URL refresh interrupted for {}", pKey);
            return Optional.empty();
        } catch (Exception e) {
            log.debug("Failed to refresh Mapillary image URL for {}: {}", pKey, e.getMessage());
            return Optional.empty();
        }
    }

    private static Optional<String> extractPKey(String sourcePageUrl) {
        Matcher matcher = PKEY_PATTERN.matcher(sourcePageUrl);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_BASE_URL;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
