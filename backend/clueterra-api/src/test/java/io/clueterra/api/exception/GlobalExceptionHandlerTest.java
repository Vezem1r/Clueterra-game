package io.clueterra.api.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.util.Set;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GameException SESSION_NOT_FOUND -> 404 + errorCode in body")
    void gameException_sessionNotFound_returns404() throws Exception {
        mockMvc.perform(get("/stub/game?type=SESSION_NOT_FOUND"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("GameException NO_ATTEMPTS_LEFT -> 400 + errorCode in body")
    void gameException_noAttempts_returns400() throws Exception {
        mockMvc.perform(get("/stub/game?type=NO_ATTEMPTS_LEFT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_ATTEMPTS_LEFT"));
    }

    @Test
    @DisplayName("GameException ALREADY_PLAYED_TODAY -> 409 Conflict")
    void gameException_alreadyPlayed_returns409() throws Exception {
        mockMvc.perform(get("/stub/game?type=ALREADY_PLAYED_TODAY"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_PLAYED_TODAY"));
    }

    @Test
    @DisplayName("GameException SESSION_ACCESS_DENIED -> 403 Forbidden")
    void gameException_accessDenied_returns403() throws Exception {
        mockMvc.perform(get("/stub/game?type=SESSION_ACCESS_DENIED"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SESSION_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("A stale game round raised by SQL -> 409 ROUND_NOT_CURRENT")
    void staleGameRound_returns409() throws Exception {
        mockMvc.perform(get("/stub/sql-state?state=CT409"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_NOT_CURRENT"));
    }

    @Test
    @DisplayName("An invalid game answer raised by SQL -> 400 INVALID_ANSWER")
    void invalidGameAnswer_returns400() throws Exception {
        mockMvc.perform(get("/stub/sql-state?state=CT400"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ANSWER"));
    }

    @Test
    @DisplayName("Any other SQL failure stays a 500 without details")
    void otherSqlFailure_returns500() throws Exception {
        mockMvc.perform(get("/stub/sql-state?state=23505"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @Test
    @DisplayName("@Valid failure -> 400 with per-field errors map")
    void validationError_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/stub/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}")) // blank name violates @NotBlank
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("ConstraintViolationException -> 400")
    void constraintViolation_returns400() throws Exception {
        mockMvc.perform(get("/stub/constraint"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("ResourceNotFoundException -> 404")
    void resourceNotFound_returns404() throws Exception {
        mockMvc.perform(get("/stub/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("resource missing"));
    }

    @Test
    @DisplayName("ResourceConflictException -> 409")
    void resourceConflict_returns409() throws Exception {
        mockMvc.perform(get("/stub/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("BadCredentialsException -> 401 with generic message (no credential leakage)")
    void badCredentials_returns401WithGenericMessage() throws Exception {
        mockMvc.perform(get("/stub/bad-credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("AccessDeniedException -> 403")
    void springAccessDenied_returns403() throws Exception {
        mockMvc.perform(get("/stub/spring-access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("unhandled RuntimeException -> 500 with generic message (no stack trace leakage)")
    void unknownException_returns500WithGenericMessage() throws Exception {
        mockMvc.perform(get("/stub/internal-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(jsonPath("$.status").value(500));
    }

    @Test
    @DisplayName("ApiErrorResponse always contains timestamp, status, error, message fields")
    void responseAlwaysContainsRequiredFields() throws Exception {
        mockMvc.perform(get("/stub/not-found"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").isNumber())
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @RestController
    static class StubController {

        @GetMapping("/stub/game")
        void triggerGameException(@RequestParam String type) {
            throw switch (type) {
                case "SESSION_NOT_FOUND" -> GameException.sessionNotFound(123L);
                case "NO_ATTEMPTS_LEFT" -> GameException.noMoreAttempts();
                case "ALREADY_PLAYED_TODAY" -> GameException.alreadyPlayedToday();
                case "SESSION_ACCESS_DENIED" -> GameException.sessionAccessDenied();
                default -> throw new IllegalArgumentException("unknown type: " + type);
            };
        }

        @PostMapping("/stub/validate")
        void triggerValidation(@Valid @RequestBody ValidatedBody ignore) {
            // trigger
        }

        @GetMapping("/stub/not-found")
        void triggerNotFound() {
            throw new ResourceNotFoundException("resource missing");
        }

        @GetMapping("/stub/conflict")
        void triggerConflict() {
            throw new ResourceConflictException("duplicate resource");
        }

        @GetMapping("/stub/bad-credentials")
        void triggerBadCredentials() {
            throw new BadCredentialsException("wrong password");
        }

        @GetMapping("/stub/spring-access-denied")
        void triggerSpringAccessDenied() {
            throw new AccessDeniedException("denied");
        }

        @GetMapping("/stub/sql-state")
        void triggerSqlState(@RequestParam String state) {
            throw new DataIntegrityViolationException("game function failed", new SQLException("game error", state));
        }

        @GetMapping("/stub/internal-error")
        void triggerInternalError() {
            throw new RuntimeException("something exploded");
        }

        @GetMapping("/stub/constraint")
        void triggerConstraintViolation() {
            throw new ConstraintViolationException("page must be greater than or equal to 0", Set.of());
        }

        record ValidatedBody(@NotBlank(message = "must not be blank") String name) {
        }
    }
}
