package io.clueterra.questions.repository.jdbc;

import io.clueterra.questions.entity.enums.GameMode;
import io.hypersistence.tsid.TSID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class HintUsageLogJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String INSERT_SQL = """
            INSERT INTO hint_usage_log (id, hint_id, country_id, used_date, game_mode, challenge_id)
            VALUES (:id, :hintId, :countryId, :usedDate, :gameMode, :challengeId)
            ON CONFLICT (hint_id, used_date, game_mode) DO NOTHING
            """;
    private static final String DELETE_BATCH_SQL = """
            DELETE FROM hint_usage_log
            WHERE hint_id IN (:hintIds)
              AND used_date = :usedDate
              AND game_mode = :gameMode
            """;

    public void save(Long hintId, Long countryId, LocalDate usedDate, GameMode gameMode, Long challengeId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", TSID.Factory.getTsid().toLong())
                .addValue("hintId", hintId)
                .addValue("countryId", countryId)
                .addValue("usedDate", Date.valueOf(usedDate))
                .addValue("gameMode", gameMode.name())
                .addValue("challengeId", challengeId);

        jdbc.update(INSERT_SQL, params);
    }

    public void saveBatch(List<Long> hintIds, Long countryId, LocalDate usedDate, GameMode gameMode) {
        if (hintIds == null || hintIds.isEmpty()) return;

        MapSqlParameterSource[] batchParams = hintIds.stream()
                .map(hintId -> new MapSqlParameterSource()
                        .addValue("id", TSID.Factory.getTsid().toLong())
                        .addValue("hintId", hintId)
                        .addValue("countryId", countryId)
                        .addValue("usedDate", Date.valueOf(usedDate))
                        .addValue("gameMode", gameMode.name())
                        .addValue("challengeId", (Long) null))
                .toArray(MapSqlParameterSource[]::new);

        jdbc.batchUpdate(INSERT_SQL, batchParams);
    }

    public void deleteBatch(List<Long> hintIds, LocalDate usedDate, GameMode gameMode) {
        if (hintIds == null || hintIds.isEmpty()) return;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("hintIds", hintIds)
                .addValue("usedDate", Date.valueOf(usedDate))
                .addValue("gameMode", gameMode.name());

        jdbc.update(DELETE_BATCH_SQL, params);
    }

    public List<Long> findRecentlyUsedHintIds(Long countryId, GameMode gameMode, LocalDate since) {
        return jdbc.queryForList("""
                SELECT hint_id FROM hint_usage_log
                WHERE country_id = :countryId AND game_mode = :gameMode AND used_date > :since
                """,
                Map.of("countryId", countryId, "gameMode", gameMode.name(), "since", Date.valueOf(since)),
                Long.class
        );
    }
}
