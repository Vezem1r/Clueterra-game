package io.clueterra.api.admin.settings.repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.api.common.utils.loader.SqlLoader;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class SettingsJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String GET_SETTING = """
            SELECT value FROM app_settings WHERE key = :key
            """;

    private static final String UPSERT_SETTING = """
            INSERT INTO app_settings (key, value, updated_at) VALUES (:key, :value, NOW())
            ON CONFLICT (key) DO UPDATE SET value = :value, updated_at = NOW()
            """;

    private static final String INSERT_AUDIT = """
            INSERT INTO app_settings_audit (
                change_batch_id, setting_key, old_value, new_value,
                changed_by_user_id, changed_by_email, changed_by_username, changed_at
            ) VALUES (
                :changeBatchId, :settingKey, :oldValue, :newValue,
                :changedByUserId, :changedByEmail, :changedByUsername, NOW()
            )
            """;

    private static final String RECALCULATE_ALL_LEVELS = """
            UPDATE user_profiles SET
                current_level = GREATEST(
                    current_level,
                    CAST(FLOOR(SQRT(CAST(total_xp AS double precision) / CAST(:levelDivisor AS double precision))) + 1 AS integer)
                ),
                updated_at = NOW()
            WHERE total_xp > 0
            """;

    private static final String FIND_RECENT_AUDIT = SqlLoader.load(SettingsJdbcRepository.class, "findRecentAudit");
    private static final String COUNT_AUDIT_BATCHES = """
            SELECT COUNT(DISTINCT change_batch_id)
            FROM app_settings_audit
            """;

    public Optional<String> getSetting(String key) {
        var params = new MapSqlParameterSource("key", key);
        return jdbc.query(GET_SETTING, params, (rs, rowNum) -> rs.getString("value"))
                .stream()
                .findFirst();
    }

    public void setSetting(String key, String value) {
        var params = new MapSqlParameterSource()
                .addValue("key", key)
                .addValue("value", value);
        jdbc.update(UPSERT_SETTING, params);
    }

    public void insertAuditRow(
            UUID changeBatchId,
            String settingKey,
            String oldValue,
            String newValue,
            Long changedByUserId,
            String changedByEmail,
            String changedByUsername) {
        var params = new MapSqlParameterSource()
                .addValue("changeBatchId", changeBatchId)
                .addValue("settingKey", settingKey)
                .addValue("oldValue", oldValue)
                .addValue("newValue", newValue)
                .addValue("changedByUserId", changedByUserId)
                .addValue("changedByEmail", changedByEmail)
                .addValue("changedByUsername", changedByUsername);
        jdbc.update(INSERT_AUDIT, params);
    }

    public long countAuditBatches() {
        Long total = jdbc.queryForObject(COUNT_AUDIT_BATCHES, new MapSqlParameterSource(), Long.class);
        return total != null ? total : 0L;
    }

    public List<SettingsAuditRow> findRecentAudit(int limit, int offset) {
        var params = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("offset", offset);
        return jdbc.query(FIND_RECENT_AUDIT, params, (rs, rowNum) -> new SettingsAuditRow(
                rs.getLong("id"),
                rs.getObject("change_batch_id", UUID.class),
                rs.getString("setting_key"),
                rs.getString("old_value"),
                rs.getString("new_value"),
                rs.getObject("changed_at", Timestamp.class).toInstant(),
                rs.getObject("changed_by_user_id", Long.class),
                rs.getString("changed_by_email"),
                rs.getString("changed_by_username")));
    }

    public void recalculateAllLevels(int levelDivisor) {
        var params = new MapSqlParameterSource("levelDivisor", levelDivisor);
        jdbc.update(RECALCULATE_ALL_LEVELS, params);
    }
}
