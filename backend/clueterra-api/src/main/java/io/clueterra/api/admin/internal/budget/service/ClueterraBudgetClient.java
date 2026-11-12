package io.clueterra.api.admin.internal.budget.service;

import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.admin.internal.budget.dto.BudgetOperationAcceptedResponse;
import io.clueterra.api.admin.internal.budget.dto.BudgetServiceStatusResponse;
import io.clueterra.api.config.BudgetServiceProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClueterraBudgetClient {

    private final WebClient budgetServiceClient;
    private final BudgetServiceProperties properties;
    private final ObjectMapper objectMapper;

    public Mono<BudgetServiceStatusResponse> getStatus() {
        return budgetServiceClient
                .get()
                .uri("/actuator/health")
                .retrieve()
                .toBodilessEntity()
                .map(response -> new BudgetServiceStatusResponse("UP", null))
                .timeout(timeout())
                .onErrorResume(
                        exception -> {
                            log.warn("Budget service status check failed: {}", exception.getMessage());
                            return Mono.just(unavailableStatus(exception));
                        });
    }

    public Mono<BudgetOperationAcceptedResponse> startOperation(String path) {
        return budgetServiceClient
                .post()
                .uri(path)
                .retrieve()
                .bodyToMono(BudgetOperationAcceptedResponse.class)
                .timeout(timeout())
                .onErrorMap(WebClientResponseException.class, this::wrapError);
    }

    private Duration timeout() {
        return properties.getTimeout();
    }

    private BudgetServiceStatusResponse unavailableStatus(Throwable exception) {
        String error =
                exception.getMessage() == null || exception.getMessage().isBlank()
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage();
        return new BudgetServiceStatusResponse("DOWN", error);
    }

    private ResponseStatusException wrapError(WebClientResponseException exception) {
        HttpStatusCode statusCode = exception.getStatusCode();
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String message = extractMessage(exception.getResponseBodyAsString());
        log.warn("Budget service operation rejected: {} {}", statusCode, message);
        return new ResponseStatusException(
                status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status, message);
    }

    private String extractMessage(String responseBody) {
        try {
            JsonNode body = objectMapper.readTree(responseBody);
            if (body.hasNonNull("message")) {
                return body.path("message").asText();
            }
        } catch (Exception exception) {
            log.debug("Unable to parse Budget service error response", exception);
        }
        return "Budget service operation failed";
    }
}
