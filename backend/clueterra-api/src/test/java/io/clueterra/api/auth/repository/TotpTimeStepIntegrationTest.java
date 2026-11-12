package io.clueterra.api.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import io.clueterra.api.TestcontainersConfiguration;

@JdbcTest(
        properties = {
                "spring.flyway.enabled=true",
                "spring.flyway.locations=classpath:db/migration",
                "spring.flyway.schemas=cl_api_schema",
                "spring.flyway.default-schema=cl_api_schema",
                "spring.flyway.create-schemas=true",
                "spring.datasource.hikari.connection-init-sql=SET search_path TO cl_api_schema, cl_schema"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, AuthJdbcRepository.class})
class TotpTimeStepIntegrationTest {

    private static final long USER_ID = 1L;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private AuthJdbcRepository repository;

    @BeforeEach
    void insertUser() {
        jdbc.getJdbcTemplate().execute("TRUNCATE cl_api_schema.users CASCADE");
        jdbc.update("""
                INSERT INTO users (id, email, username, password_hash, role, enabled)
                VALUES (:id, 'admin@example.com', 'admin', 'hash', 'ADMIN', TRUE)
                """, Map.of("id", USER_ID));
    }

    @Test
    void acceptsEachTimeStepOnlyOnceAndNeverAnOlderOne() {
        assertThat(repository.recordTotpTimeStep(USER_ID, 100)).isTrue();
        assertThat(repository.recordTotpTimeStep(USER_ID, 100)).isFalse();
        assertThat(repository.recordTotpTimeStep(USER_ID, 99)).isFalse();
        assertThat(repository.recordTotpTimeStep(USER_ID, 101)).isTrue();
    }

    @Test
    void storesFullLengthEncryptedSecrets() {
        String encrypted = "A".repeat(80);

        repository.updateTotpSecret(USER_ID, encrypted);

        assertThat(repository.findById(USER_ID)).hasValueSatisfying(
                user -> assertThat(user.totpSecret()).isEqualTo(encrypted));
    }
}
