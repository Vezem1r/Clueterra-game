package io.clueterra.questions.controller;

import java.time.Clock;
import java.time.Instant;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import io.clueterra.questions.clients.common.DataSourceException;
import io.clueterra.questions.controller.dto.ApiErrorResponse;
import io.clueterra.questions.exception.ConflictException;
import io.clueterra.questions.exception.ResourceNotFoundException;
import io.clueterra.questions.exception.ServiceUnavailableException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Clock clock;

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return error(HttpStatus.NOT_FOUND, "Resource Not Found", ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        log.warn("Conflict: {}", ex.getMessage());
        return error(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnavailable(ServiceUnavailableException ex, HttpServletRequest request) {
        log.warn("Service unavailable: {}", ex.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Invalid argument: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, "Invalid Argument", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", errors);
        return error(HttpStatus.BAD_REQUEST, "Validation Failed", errors, request);
    }

    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class,
            MissingServletRequestParameterException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiErrorResponse> handleParameterValidation(Exception ex, HttpServletRequest request) {
        log.warn("Request parameter validation failed: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, "Validation Failed", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = String.format("Parameter '%s' should be of type '%s'", ex.getName(), ex.getRequiredType() != null
                ? ex.getRequiredType().getSimpleName() : "unknown");
        log.warn("Type mismatch: {}", message);
        return error(HttpStatus.BAD_REQUEST, "Type Mismatch", message, request);
    }

    @ExceptionHandler(DataSourceException.class)
    public ResponseEntity<ApiErrorResponse> handleDataSourceException(DataSourceException ex, HttpServletRequest request) {
        log.error("Data source error: {}", ex.getSource(), ex);
        return error(HttpStatus.SERVICE_UNAVAILABLE, "External Service Error",
                "External data source temporarily unavailable: " + ex.getSource(), request);
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientAbort(AsyncRequestNotUsableException ex) {
        log.debug("Client disconnected mid-response: {}", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleAllExceptions(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {}", request.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred. Please try again later.", request);
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String error, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(clock), status.value(), error, message, request.getRequestURI()));
    }
}
