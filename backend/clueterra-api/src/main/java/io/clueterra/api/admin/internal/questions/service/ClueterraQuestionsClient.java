package io.clueterra.api.admin.internal.questions.service;

import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClueterraQuestionsClient {

    private final WebClient questionsServiceClient;
    private final ObjectMapper objectMapper;

    @Value("${clueterra.questions-service.timeouts.default:30s}")
    private Duration timeout;

    public void fireAndForget(String label, String uri) {
        questionsServiceClient.post().uri(uri)
                .retrieve()
                .toBodilessEntity()
                .timeout(timeout)
                .subscribe(
                        r -> log.debug("{} request delivered", label),
                        e -> log.warn("{} delivery failed: {}", label, e.getMessage())
                );
    }

    public Mono<Map<String, Object>> getHealth() {
        return questionsServiceClient.get()
                .uri("/api/admin/health")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .timeout(timeout)
                .onErrorResume(ex -> {
                    log.error("Questions service health check failed: {}", ex.getMessage());
                    String detail = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                    return Mono.just(Map.of(
                            "status", "DOWN",
                            "error", "Connection failed",
                            "details", detail));
                });
    }

    public Mono<Map<String, Object>> getSourcesOverview() {
        return questionsServiceClient.get()
                .uri("/api/admin")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .timeout(timeout)
                .onErrorMap(WebClientResponseException.class, this::wrapError)
                .onErrorResume(ex -> !(ex instanceof ResponseStatusException), ex -> {
                    log.error("Questions service sources overview failed: {}", ex.getMessage());
                    String detail = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                    return Mono.just(Map.of(
                            "status", "DOWN",
                            "error", "Connection failed",
                            "details", detail));
                });
    }

    public Mono<JsonNode> getJson(String uri) {
        return questionsServiceClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(timeout)
                .onErrorMap(WebClientResponseException.class, this::wrapError);
    }

    public Mono<JsonNode> postJson(String uri) {
        return questionsServiceClient.post()
                .uri(uri)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(timeout)
                .onErrorMap(WebClientResponseException.class, this::wrapError);
    }

    public <T> Mono<T> postWithBody(String uri, Object body, Class<T> responseType) {
        return questionsServiceClient.post()
                .uri(uri)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(responseType)
                .timeout(timeout)
                .onErrorMap(WebClientResponseException.class, this::wrapError);
    }

    private ResponseStatusException wrapError(WebClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        HttpStatusCode statusCode = ex.getStatusCode();
        log.error("Questions service error: {} {}", statusCode, body);

        String message = extractMessage(body);
        HttpStatus resolvedStatus = HttpStatus.resolve(statusCode.value());
        if (resolvedStatus == null) {
            resolvedStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return new ResponseStatusException(resolvedStatus, message);
    }

    private String extractMessage(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.has("message") && !node.get("message").isNull()) {
                return node.get("message").asText();
            }
        } catch (Exception ignored) {
        }
        return "Questions service error";
    }
}
