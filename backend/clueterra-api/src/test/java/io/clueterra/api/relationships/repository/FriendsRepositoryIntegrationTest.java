package io.clueterra.api.relationships.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import io.clueterra.api.TestcontainersConfiguration;
import io.clueterra.api.friends.repository.FriendsJdbcRepository;

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
@Import({TestcontainersConfiguration.class, FriendsJdbcRepository.class})
class FriendsRepositoryIntegrationTest {

    private static final String TRUNCATE_TEST_DATA = "TRUNCATE cl_api_schema.users CASCADE";

    private static final String INSERT_USER = """
            INSERT INTO users (id, email, username, password_hash, role, enabled, is_guest, social_discoverable)
            VALUES (:id, :email, :username, 'hash', 'USER', TRUE, FALSE, TRUE)
            """;

    private static final String INSERT_PROFILE = """
            INSERT INTO user_profiles (user_id, display_name, total_xp, current_level)
            VALUES (:userId, :displayName, :totalXp, :level)
            """;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private FriendsJdbcRepository friendsRepository;

    @BeforeEach
    void cleanData() {
        jdbc.getJdbcTemplate().execute(TRUNCATE_TEST_DATA);
        insertUser(1L, "atlas", "Atlas", 400, 7);
        insertUser(2L, "terra", "Terra", 260, 5);
        insertUser(3L, "nova", "Nova", 100, 2);
    }

    @Test
    @DisplayName("friend request can be accepted and blocking removes the friendship")
    void friendRequestLifecycle() {
        Optional<Long> requestId = friendsRepository.insertFriendRequest(1L, 2L);

        assertThat(requestId).isPresent();
        assertThat(friendsRepository.findIncomingRequests(2L)).hasSize(1);
        assertThat(friendsRepository.findOutgoingRequests(1L)).hasSize(1);
        assertThat(friendsRepository.acceptFriendRequest(2L, requestId.orElseThrow())).isTrue();
        assertThat(friendsRepository.findFriends(1L)).hasSize(1);
        assertThat(friendsRepository.findFriendsLeaderboard(1L, "week", LocalDate.parse("2026-06-22")))
                .hasSize(2);

        friendsRepository.blockUser(1L, 2L);

        assertThat(friendsRepository.findFriends(1L)).isEmpty();
        assertThat(friendsRepository.insertFriendRequest(2L, 1L)).isEmpty();
    }

    private void insertUser(long id, String username, String displayName, long totalXp, int level) {
        jdbc.update(INSERT_USER, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("email", username + "@example.com")
                .addValue("username", username));
        jdbc.update(INSERT_PROFILE, new MapSqlParameterSource()
                .addValue("userId", id)
                .addValue("displayName", displayName)
                .addValue("totalXp", totalXp)
                .addValue("level", level));
    }
}
