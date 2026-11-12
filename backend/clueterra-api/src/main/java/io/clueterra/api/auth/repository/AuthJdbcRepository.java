package io.clueterra.api.auth.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import io.clueterra.api.auth.dto.guestClaim.GuestProgressClaimRow;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.api.common.utils.loader.SqlLoader;
import io.hypersistence.tsid.TSID;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AuthJdbcRepository {

    private static final int GUEST_TOKEN_EXPIRY_DAYS = 30;

    private final NamedParameterJdbcTemplate jdbc;

    private static final String INSERT_USER_WITH_PROFILE = SqlLoader.load(AuthJdbcRepository.class, "insertUserWithProfile");
    private static final String INSERT_OAUTH_USER_WITH_PROFILE = SqlLoader.load(AuthJdbcRepository.class, "insertOauthUserWithProfile");
    private static final String INSERT_GUEST_USER_WITH_PROFILE = SqlLoader.load(AuthJdbcRepository.class, "insertGuestUserWithProfile");
    private static final String MERGE_GUEST_TO_REGISTERED = SqlLoader.load(AuthJdbcRepository.class, "mergeGuestToRegistered");
    private static final String MERGE_GUEST_TO_OAUTH = SqlLoader.load(AuthJdbcRepository.class, "mergeGuestToOAuth");
    private static final String DELETE_EXPIRED_GUESTS = SqlLoader.load(AuthJdbcRepository.class, "deleteExpiredGuests");

    private static final String FIND_BY_EMAIL = """
            SELECT id, email, password_hash, username, role, enabled,
                   totp_secret, totp_enabled, oauth_provider, oauth_id,
                   terms_version_accepted, privacy_version_accepted,
                   is_guest, guest_token
            FROM users WHERE LOWER(email) = LOWER(:email)
            """;

    private static final String FIND_BY_ID = """
            SELECT id, email, password_hash, username, role, enabled,
                   totp_secret, totp_enabled, oauth_provider, oauth_id,
                   terms_version_accepted, privacy_version_accepted,
                   is_guest, guest_token
            FROM users WHERE id = :id
            """;

    private static final String FIND_BY_OAUTH = """
            SELECT id, email, password_hash, username, role, enabled,
                   totp_secret, totp_enabled, oauth_provider, oauth_id,
                   terms_version_accepted, privacy_version_accepted,
                   is_guest, guest_token
            FROM users WHERE oauth_provider = :provider AND oauth_id = :oauthId
            """;

    private static final String FIND_BY_GUEST_TOKEN = """
            SELECT id, email, password_hash, username, role, enabled,
                   totp_secret, totp_enabled, oauth_provider, oauth_id,
                   terms_version_accepted, privacy_version_accepted,
                   is_guest, guest_token
            FROM users
            WHERE guest_token = :guestToken
              AND is_guest = true
              AND guest_token_expires_at > NOW()
            """;

    private static final String EXISTS_BY_EMAIL = """
            SELECT EXISTS(SELECT 1 FROM users WHERE LOWER(email) = LOWER(:email))
            """;

    private static final String EXISTS_BY_USERNAME = """
            SELECT EXISTS(SELECT 1 FROM users WHERE LOWER(username) = LOWER(:username))
            """;

    private static final String UPDATE_TOTP_SECRET = """
            UPDATE users SET totp_secret = :secret, updated_at = NOW() WHERE id = :id
            """;

    private static final String ENABLE_TOTP = """
            UPDATE users SET totp_enabled = true, updated_at = NOW() WHERE id = :id
            """;

    private static final String RECORD_TOTP_TIME_STEP = """
            UPDATE users SET totp_last_used_step = :step
            WHERE id = :id AND (totp_last_used_step IS NULL OR totp_last_used_step < :step)
            """;

    private static final String REFRESH_GUEST_TOKEN_EXPIRY = """
            UPDATE users SET guest_token_expires_at = :expiresAt, updated_at = NOW()
            WHERE id = :id AND is_guest = true
            """;

    private static final String EXPIRE_GUEST_TOKEN = """
            UPDATE users
            SET guest_token = NULL,
                guest_token_expires_at = NOW() - INTERVAL '1 second',
                updated_at = NOW()
            WHERE id = :id AND is_guest = true
            """;

    private static final String CLAIM_GUEST_TODAY_PROGRESS = """
            SELECT claim_status, guest_user_id, target_user_id, session_id, session_status
            FROM claim_guest_today_progress(:guestToken, :targetUserId)
            """;

    private static final RowMapper<UserRow> ROW_MAPPER =
            (rs, rowNum) -> new UserRow(
                    rs.getLong("id"),
                    rs.getString("email"),
                    rs.getString("password_hash"),
                    rs.getString("username"),
                    rs.getString("role"),
                    rs.getBoolean("enabled"),
                    rs.getString("totp_secret"),
                    rs.getBoolean("totp_enabled"),
                    rs.getString("oauth_provider"),
                    rs.getString("oauth_id"),
                    rs.getString("terms_version_accepted"),
                    rs.getString("privacy_version_accepted"),
                    rs.getBoolean("is_guest"),
                    rs.getString("guest_token"));

    public long insert(String email, String username, String passwordHash, String termsVersion, String privacyVersion) {
        long id = TSID.Factory.getTsid().toLong();
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("email", email)
                .addValue("username", username)
                .addValue("passwordHash", passwordHash)
                .addValue("termsVersion", termsVersion)
                .addValue("privacyVersion", privacyVersion);
        jdbc.update(INSERT_USER_WITH_PROFILE, params);
        return id;
    }

    public long insertOAuthUser(String email, String username, String oauthProvider, String oauthId,
                                String avatarUrl, String termsVersion, String privacyVersion) {
        long id = TSID.Factory.getTsid().toLong();
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("email", email)
                .addValue("username", username)
                .addValue("oauthProvider", oauthProvider)
                .addValue("oauthId", oauthId)
                .addValue("avatarUrl", avatarUrl)
                .addValue("termsVersion", termsVersion)
                .addValue("privacyVersion", privacyVersion);
        jdbc.update(INSERT_OAUTH_USER_WITH_PROFILE, params);
        return id;
    }

    public long insertGuestUser(String termsVersion, String privacyVersion) {
        long id = TSID.Factory.getTsid().toLong();
        String guestToken = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(GUEST_TOKEN_EXPIRY_DAYS, ChronoUnit.DAYS);
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("guestToken", guestToken)
                .addValue("expiresAt", Timestamp.from(expiresAt))
                .addValue("termsVersion", termsVersion)
                .addValue("privacyVersion", privacyVersion);
        jdbc.update(INSERT_GUEST_USER_WITH_PROFILE, params);
        return id;
    }

    public Optional<UserRow> findByGuestToken(String guestToken) {
        var params = new MapSqlParameterSource("guestToken", guestToken);
        return jdbc.query(FIND_BY_GUEST_TOKEN, params, ROW_MAPPER).stream().findFirst();
    }

    public GuestProgressClaimRow claimGuestTodayProgress(String guestToken, Long targetUserId) {
        var params = new MapSqlParameterSource()
                .addValue("guestToken", guestToken)
                .addValue("targetUserId", targetUserId);
        return jdbc.query(CLAIM_GUEST_TODAY_PROGRESS, params, (rs, rowNum) -> new GuestProgressClaimRow(
                        rs.getString("claim_status"),
                        (Long) rs.getObject("guest_user_id"),
                        (Long) rs.getObject("target_user_id"),
                        (Long) rs.getObject("session_id"),
                        rs.getString("session_status")))
                .stream()
                .findFirst()
                .orElse(new GuestProgressClaimRow("NO_GUEST", null, targetUserId, null, null));
    }

    public Optional<Long> mergeGuestToRegistered(String guestToken, String email, String username, String passwordHash,
                                                 String termsVersion, String privacyVersion) {
        var params = new MapSqlParameterSource()
                .addValue("guestToken", guestToken)
                .addValue("email", email)
                .addValue("username", username)
                .addValue("passwordHash", passwordHash)
                .addValue("termsVersion", termsVersion)
                .addValue("privacyVersion", privacyVersion);
        return jdbc.query(MERGE_GUEST_TO_REGISTERED, params, (rs, rowNum) -> rs.getLong("id"))
                .stream()
                .findFirst();
    }

    public Optional<Long> mergeGuestToOAuth(String guestToken, String email, String username, String oauthProvider,
                                            String oauthId, String avatarUrl, String termsVersion, String privacyVersion) {
        var params = new MapSqlParameterSource()
                .addValue("guestToken", guestToken)
                .addValue("email", email)
                .addValue("username", username)
                .addValue("oauthProvider", oauthProvider)
                .addValue("oauthId", oauthId)
                .addValue("avatarUrl", avatarUrl)
                .addValue("termsVersion", termsVersion)
                .addValue("privacyVersion", privacyVersion);
        return jdbc.query(MERGE_GUEST_TO_OAUTH, params, (rs, rowNum) -> rs.getLong("id"))
                .stream()
                .findFirst();
    }

    public Optional<UserRow> findByEmail(String email) {
        var params = new MapSqlParameterSource("email", email);
        return jdbc.query(FIND_BY_EMAIL, params, ROW_MAPPER).stream().findFirst();
    }

    public Optional<UserRow> findById(Long id) {
        var params = new MapSqlParameterSource("id", id);
        return jdbc.query(FIND_BY_ID, params, ROW_MAPPER).stream().findFirst();
    }

    public Optional<UserRow> findByOAuth(String provider, String oauthId) {
        var params = new MapSqlParameterSource()
                .addValue("provider", provider)
                .addValue("oauthId", oauthId);
        return jdbc.query(FIND_BY_OAUTH, params, ROW_MAPPER).stream().findFirst();
    }

    public boolean existsByEmail(String email) {
        var params = new MapSqlParameterSource("email", email);
        return Boolean.TRUE.equals(jdbc.queryForObject(EXISTS_BY_EMAIL, params, Boolean.class));
    }

    public boolean existsByUsername(String username) {
        var params = new MapSqlParameterSource("username", username);
        return Boolean.TRUE.equals(jdbc.queryForObject(EXISTS_BY_USERNAME, params, Boolean.class));
    }

    public void updateTotpSecret(Long userId, String secret) {
        var params = new MapSqlParameterSource()
                .addValue("id", userId)
                .addValue("secret", secret);
        jdbc.update(UPDATE_TOTP_SECRET, params);
    }

    public void enableTotp(Long userId) {
        var params = new MapSqlParameterSource("id", userId);
        jdbc.update(ENABLE_TOTP, params);
    }

    public boolean recordTotpTimeStep(long userId, long step) {
        var params = new MapSqlParameterSource()
                .addValue("id", userId)
                .addValue("step", step);
        return jdbc.update(RECORD_TOTP_TIME_STEP, params) == 1;
    }

    public void refreshGuestTokenExpiry(Long userId) {
        Instant expiresAt = Instant.now().plus(GUEST_TOKEN_EXPIRY_DAYS, ChronoUnit.DAYS);
        var params = new MapSqlParameterSource()
                .addValue("id", userId)
                .addValue("expiresAt", Timestamp.from(expiresAt));
        jdbc.update(REFRESH_GUEST_TOKEN_EXPIRY, params);
    }

    public void expireGuestToken(long userId) {
        jdbc.update(EXPIRE_GUEST_TOKEN, new MapSqlParameterSource("id", userId));
    }

    public int deleteExpiredGuests() {
        Integer deleted = jdbc.queryForObject(DELETE_EXPIRED_GUESTS, new MapSqlParameterSource(), Integer.class);
        return deleted != null ? deleted : 0;
    }
}
