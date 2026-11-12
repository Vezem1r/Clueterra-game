package io.clueterra.api.exception;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.validation.ConstraintViolationException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import io.clueterra.api.admin.hints.image.WikimediaImageException;
import io.clueterra.api.budget.exception.BudgetGameException;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(RegistrationDisabledException.class)
    public ResponseEntity<ApiErrorResponse> handleRegistrationDisabled(
            RegistrationDisabledException e) {
        return buildResponse(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(ProfileLockedException.class)
    public ResponseEntity<ApiErrorResponse> handleProfileLocked(ProfileLockedException e) {
        return buildResponse(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleEmailAlreadyExists(
            EmailAlreadyExistsException e) {
        return buildResponse(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException e) {
        return buildResponse(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public ResponseEntity<ApiErrorResponse> handleTooManyLoginAttempts(TooManyLoginAttemptsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(ApiErrorResponse.ofRateLimit(e.getRetryAfterSeconds()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(ResourceNotFoundException e) {
        return buildResponse(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceConflict(ResourceConflictException e) {
        return buildResponse(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult()
                .getAllErrors()
                .forEach(
                        error -> {
                            String fieldName = ((FieldError) error).getField();
                            String errorMessage = error.getDefaultMessage();
                            errors.put(fieldName, errorMessage);
                        });
        return ResponseEntity.badRequest().body(ApiErrorResponse.ofValidation(errors));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleHandlerMethodValidation(
            HandlerMethodValidationException e) {
        Map<String, String> errors = new HashMap<>();
        for (ParameterValidationResult validationResult : e.getParameterValidationResults()) {
            String parameterName = Optional.ofNullable(validationResult.getMethodParameter().getParameterName())
                    .orElse("request");
            String errorMessage = validationResult.getResolvableErrors().stream()
                    .map(MessageSourceResolvable::getDefaultMessage)
                    .filter(message -> message != null && !message.isBlank())
                    .findFirst()
                    .orElse("Validation failed");
            errors.put(parameterName, errorMessage);
        }
        if (!errors.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiErrorResponse.ofValidation(errors));
        }
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        Map<String, String> errors = new HashMap<>();
        e.getConstraintViolations().forEach(violation -> {
            String path = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "request";
            int lastDot = path.lastIndexOf('.');
            String field = lastDot >= 0 ? path.substring(lastDot + 1) : path;
            errors.put(field, violation.getMessage());
        });
        if (!errors.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiErrorResponse.ofValidation(errors));
        }
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ignored) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Request body is missing or malformed");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        String parameter = e.getName();
        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid value for " + parameter);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingServletRequestParameter(MissingServletRequestParameterException e) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Missing required parameter: " + e.getParameterName());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingRequestHeader(MissingRequestHeaderException e) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Missing required header: " + e.getHeaderName());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ignored) {
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed");
    }

    @ExceptionHandler(GameException.class)
    public ResponseEntity<ApiErrorResponse> handleGameException(GameException e) {
        return ResponseEntity.status(e.getStatus())
                .body(ApiErrorResponse.ofGame(e.getStatus(), e.getMessage(), e.getErrorCode()));
    }

    @ExceptionHandler(BudgetGameException.class)
    public ResponseEntity<ApiErrorResponse> handleBudgetGameException(BudgetGameException e) {
        return ResponseEntity.status(e.getStatus())
                .body(ApiErrorResponse.ofGame(e.getStatus(), e.getMessage(), e.getErrorCode()));
    }

    @ExceptionHandler(LegalAcceptanceRequiredException.class)
    public ResponseEntity<ApiErrorResponse> handleLegalAcceptanceRequired(LegalAcceptanceRequiredException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.ofCode(HttpStatus.FORBIDDEN, e.getMessage(), e.getErrorCode()));
    }

    @ExceptionHandler(LegalVersionMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleLegalVersionMismatch(LegalVersionMismatchException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.ofCode(HttpStatus.CONFLICT, e.getMessage(), e.getErrorCode()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ignore) {
        return buildResponse(HttpStatus.BAD_REQUEST, "File too large. Maximum size is 5MB");
    }

    @ExceptionHandler(WikimediaImageException.class)
    public ResponseEntity<ApiErrorResponse> handleWikimediaImage(WikimediaImageException e) {
        return buildResponse(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return buildResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException e) {
        return buildResponse(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ignore) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException ignore) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiErrorResponse> handleSecurityException(SecurityException e) {
        return buildResponse(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ignore) {
        return buildResponse(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleClientDisconnect(AsyncRequestNotUsableException e) {
        log.debug("Client disconnected: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return buildResponse(status, e.getReason() != null ? e.getReason() : status.getReasonPhrase());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleDataAccess(DataAccessException e) {
        return GameSqlState.from(e)
                .map(state -> ResponseEntity.status(state.getStatus())
                        .body(ApiErrorResponse.ofGame(state.getStatus(), state.getMessage(), state.name())))
                .orElseGet(() -> handleGenericException(e));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("Resource or endpoint not found: {}", e.getResourcePath());
        return buildResponse(HttpStatus.NOT_FOUND, "Resource not found");
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(status, message));
    }
}
