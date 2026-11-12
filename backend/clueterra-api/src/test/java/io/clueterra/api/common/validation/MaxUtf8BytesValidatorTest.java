package io.clueterra.api.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.clueterra.api.auth.dto.RegisterRequest;

class MaxUtf8BytesValidatorTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("a 72-byte password is accepted")
    void acceptsPasswordAtBcryptLimit() {
        assertThat(passwordViolations("a".repeat(72))).isEmpty();
    }

    @Test
    @DisplayName("a 73-byte password is rejected")
    void rejectsPasswordOverBcryptLimit() {
        assertThat(passwordViolations("a".repeat(73)))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Password must not exceed 72 bytes");
    }

    @Test
    @DisplayName("multi-byte characters are measured in bytes, not characters")
    void measuresMultiByteCharactersInBytes() {
        assertThat(passwordViolations("п".repeat(36))).isEmpty();
        assertThat(passwordViolations("п".repeat(37))).hasSize(1);
    }

    private Set<ConstraintViolation<RegisterRequest>> passwordViolations(String password) {
        RegisterRequest request = new RegisterRequest(
                "user@example.com", "user", password, "2026-04-21", "2026-04-21");
        return validator.validateProperty(request, "password");
    }
}
