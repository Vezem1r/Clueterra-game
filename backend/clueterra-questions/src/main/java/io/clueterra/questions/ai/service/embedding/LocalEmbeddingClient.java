package io.clueterra.questions.ai.service.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.ai.config.AiProperties;
import io.clueterra.questions.ai.config.embedding.AiEmbeddingProperties;
import io.clueterra.questions.ai.model.embedding.TeiEmbeddingRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "clueterra.ai.embedding", name = "enabled", havingValue = "true")
public class LocalEmbeddingClient {

    private static final String CONTENT_TYPE = "application/json";
    private static final int ERROR_BODY_LIMIT = 500;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI embedUri;
    private final URI healthUri;
    private final Duration requestTimeout;
    private final Duration healthTimeout;

    public LocalEmbeddingClient(AiProperties aiProperties, ObjectMapper objectMapper) {
        AiEmbeddingProperties properties = aiProperties.getEmbedding();
        String baseUrl = properties.getBaseUrl().replaceFirst("/+$", "");
        this.objectMapper = objectMapper;
        this.requestTimeout = Duration.ofSeconds(properties.getTimeoutSeconds());
        this.healthTimeout = Duration.ofSeconds(properties.getHealthTimeoutSeconds());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(healthTimeout)
                .build();
        this.embedUri = URI.create(baseUrl + "/embed");
        this.healthUri = URI.create(baseUrl + "/health");
    }

    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        try {
            String requestBody = objectMapper.writeValueAsString(new TeiEmbeddingRequest(List.copyOf(texts)));
            HttpRequest request = HttpRequest.newBuilder(embedUri)
                    .timeout(requestTimeout)
                    .header("Content-Type", CONTENT_TYPE)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Local embedding service returned HTTP " + response.statusCode()
                        + ": " + abbreviate(response.body()));
            }
            return Arrays.asList(objectMapper.readValue(response.body(), float[][].class));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Local embedding request was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Local embedding request failed", exception);
        }
    }

    public boolean isAvailable() {
        HttpRequest request = HttpRequest.newBuilder(healthUri)
                .timeout(healthTimeout)
                .GET()
                .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException | RuntimeException exception) {
            log.debug("Local embedding health check failed: {}", exception.getMessage());
            return false;
        }
    }

    private String abbreviate(String value) {
        return value.length() <= ERROR_BODY_LIMIT ? value : value.substring(0, ERROR_BODY_LIMIT);
    }
}
