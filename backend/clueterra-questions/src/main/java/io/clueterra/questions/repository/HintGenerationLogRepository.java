package io.clueterra.questions.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.entity.HintGenerationLog;
import io.clueterra.questions.repository.util.SqlLoader;
import io.hypersistence.tsid.TSID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

@Repository
@Slf4j
@RequiredArgsConstructor
public class HintGenerationLogRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    private static final String INSERT_SQL = SqlLoader.load(HintGenerationLogRepository.class, "insert");

    public void save(HintGenerationLog genLog) {
        long id = genLog.id() != null ? genLog.id() : TSID.Factory.getTsid().toLong();

        String detailsJson = serializeToJson(genLog.details());
        String lensBreakdownJson = serializeToJson(genLog.lensBreakdown());

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("countryId", genLog.countryId())
                .addValue("batchId", genLog.batchId())
                .addValue("countryCode", genLog.countryCode())
                .addValue("triggerType", genLog.triggerType() != null ? genLog.triggerType().name() : null)
                .addValue("hintsAttempted", genLog.hintsAttempted())
                .addValue("hintsCreated", genLog.hintsCreated())
                .addValue("hintsDuplicates", genLog.hintsDuplicates())
                .addValue("hintsFailed", genLog.hintsFailed())
                .addValue("avgDifficulty", genLog.avgDifficulty())
                .addValue("avgQualityScore", genLog.avgQualityScore())
                .addValue("categoriesCovered", genLog.categoriesCovered())
                .addValue("hintTypesCovered", genLog.hintTypesCovered())
                .addValue("startedAt", genLog.startedAt() != null ? Timestamp.from(genLog.startedAt()) : null)
                .addValue("completedAt", genLog.completedAt() != null ? Timestamp.from(genLog.completedAt()) : null)
                .addValue("durationMs", genLog.durationMs())
                .addValue("errorMessage", genLog.errorMessage())
                .addValue("details", detailsJson)
                .addValue("lensBreakdown", lensBreakdownJson);

        jdbc.update(INSERT_SQL, params);
    }

    private String serializeToJson(Object obj) {
        if (obj == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize object to JSON", e);
            return "{}";
        }
    }
}
