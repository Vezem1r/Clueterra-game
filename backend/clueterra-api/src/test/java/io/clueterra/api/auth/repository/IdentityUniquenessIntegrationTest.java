package io.clueterra.api.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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
class IdentityUniquenessIntegrationTest {

    private static final String INSERT_USER = """
            INSERT INTO users (id, email, username, password_hash, role, enabled)
            VALUES (:id, :email, :username, 'hash', 'USER', TRUE)
            """;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private AuthJdbcRepository repository;

    @BeforeEach
    void cleanUsers() {
        jdbc.getJdbcTemplate().execute("TRUNCATE cl_api_schema.users CASCADE");
    }

    @Test
    void rejectsUsernameThatDiffersOnlyByCase() {
        insert(1L, "first@example.com", "Ivan");

        assertThatThrownBy(() -> insert(2L, "second@example.com", "ivan"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsEmailThatDiffersOnlyByCase() {
        insert(1L, "Player@Example.com", "first");

        assertThatThrownBy(() -> insert(2L, "player@example.COM", "second"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void repositoryFindsEmailAndUsernameCaseInsensitively() {
        insert(1L, "Player@Example.com", "Ivan");

        assertThat(repository.findByEmail("player@example.COM")).isPresent();
        assertThat(repository.existsByUsername("ivan")).isTrue();
    }

    private void insert(long id, String email, String username) {
        jdbc.update(INSERT_USER, Map.of("id", id, "email", email, "username", username));
    }
}
