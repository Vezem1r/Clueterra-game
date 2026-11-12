package io.clueterra.questions.repository.jdbc;

import io.clueterra.questions.entity.DataSyncLog;
import io.clueterra.questions.repository.DataSyncLogRepository;
import io.hypersistence.tsid.TSID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

@Repository
@RequiredArgsConstructor
public class DataSyncLogJdbcRepository implements DataSyncLogRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String INSERT_SQL = """
            INSERT INTO data_sync_log (id, batch_id, country_code, source, sync_type, status, countries_processed,
                statistics_saved, hints_generated, hints_deactivated, error_message, error_stack,
                fetch_details,
                started_at, completed_at, duration_ms)
            VALUES (:id, :batchId, :countryCode, :source, :syncType, :status, :countriesProcessed,
                :statisticsSaved, :hintsGenerated, :hintsDeactivated, :errorMessage, :errorStack,
                CAST(:fetchDetails AS JSONB),
                :startedAt, :completedAt, :durationMs)
            """;

    private static final String UPDATE_SQL = """
            UPDATE data_sync_log SET
                status = :status, countries_processed = :countriesProcessed,
                statistics_saved = :statisticsSaved, hints_generated = :hintsGenerated,
                hints_deactivated = :hintsDeactivated, error_message = :errorMessage,
                error_stack = :errorStack, fetch_details = CAST(:fetchDetails AS JSONB),
                completed_at = :completedAt, duration_ms = :durationMs
            WHERE id = :id
            """;

    @Override
    public DataSyncLog save(DataSyncLog log) {
        if (log.getId() == null) {
            log.setId(TSID.Factory.getTsid().toLong());
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", log.getId())
                .addValue("batchId", log.getBatchId())
                .addValue("countryCode", log.getCountryCode())
                .addValue("source", log.getSource().name())
                .addValue("syncType", log.getSyncType().name())
                .addValue("status", log.getStatus().name())
                .addValue("countriesProcessed", log.getCountriesProcessed())
                .addValue("statisticsSaved", log.getStatisticsSaved())
                .addValue("hintsGenerated", log.getHintsGenerated())
                .addValue("hintsDeactivated", log.getHintsDeactivated())
                .addValue("errorMessage", log.getErrorMessage())
                .addValue("errorStack", log.getErrorStack())
                .addValue("fetchDetails", log.getFetchDetails())
                .addValue("startedAt", log.getStartedAt() != null ? Timestamp.from(log.getStartedAt()) : null)
                .addValue("completedAt", log.getCompletedAt() != null ? Timestamp.from(log.getCompletedAt()) : null)
                .addValue("durationMs", log.getDurationMs());

        jdbc.update(INSERT_SQL, params);
        return log;
    }

    @Override
    public DataSyncLog update(DataSyncLog log) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", log.getId())
                .addValue("status", log.getStatus().name())
                .addValue("countriesProcessed", log.getCountriesProcessed())
                .addValue("statisticsSaved", log.getStatisticsSaved())
                .addValue("hintsGenerated", log.getHintsGenerated())
                .addValue("hintsDeactivated", log.getHintsDeactivated())
                .addValue("errorMessage", log.getErrorMessage())
                .addValue("errorStack", log.getErrorStack())
                .addValue("fetchDetails", log.getFetchDetails())
                .addValue("completedAt", log.getCompletedAt() != null ? Timestamp.from(log.getCompletedAt()) : null)
                .addValue("durationMs", log.getDurationMs());

        jdbc.update(UPDATE_SQL, params);
        return log;
    }

}
