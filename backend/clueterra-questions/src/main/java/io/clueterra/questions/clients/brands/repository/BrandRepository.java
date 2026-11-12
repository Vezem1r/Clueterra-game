package io.clueterra.questions.clients.brands.repository;

import java.time.Clock;
import io.clueterra.questions.clients.common.StagingRepository;
import io.clueterra.questions.clients.brands.dto.BrandRow;
import io.clueterra.questions.repository.util.SqlLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
@RequiredArgsConstructor
public class BrandRepository implements StagingRepository<BrandRow> {

    private static final String MARK_ALL_AS_NOT_FETCHED_SQL = SqlLoader.load(BrandRepository.class, "markAllAsNotFetched");
    private static final String UPSERT_BATCH_SQL = SqlLoader.load(BrandRepository.class, "upsertBatch");
    private static final String FIND_LAST_FETCHED_SQL = SqlLoader.load(BrandRepository.class, "findLastFetched");
    private static final String FIND_LAST_FETCHED_BY_COUNTRY_SQL = SqlLoader.load(BrandRepository.class, "findLastFetchedByCountryCode");

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public void markAllAsNotFetched() {
        jdbc.update(MARK_ALL_AS_NOT_FETCHED_SQL, Map.of());
    }

    public void upsertBatch(List<BrandRow> rows) {
        if (rows == null || rows.isEmpty()) return;
        Instant now = Instant.now(clock);

        SqlParameterSource[] batch = rows.stream()
                .map(r -> new MapSqlParameterSource()
                        .addValue("countryCode", r.countryCode())
                        .addValue("brandName", r.brandName())
                        .addValue("industry", r.industry())
                        .addValue("foundedYear", r.foundedYear())
                        .addValue("globalRecognition", r.globalRecognition())
                        .addValue("rank", r.rank())
                        .addValue("fetchTimestamp", Timestamp.from(now)))
                .toArray(SqlParameterSource[]::new);

        jdbc.batchUpdate(UPSERT_BATCH_SQL, batch);
    }

    public List<BrandRow> findLastFetched() {
        return jdbc.query(FIND_LAST_FETCHED_SQL, Map.of(), this::mapRow);
    }

    public List<BrandRow> findLastFetchedByCountryCode(String countryCode) {
        return jdbc.query(FIND_LAST_FETCHED_BY_COUNTRY_SQL,
                Map.of("countryCode", countryCode), this::mapRow);
    }

    private BrandRow mapRow(ResultSet rs, int rn) throws SQLException {
        return new BrandRow(
                rs.getString("country_code"),
                rs.getString("brand_name"),
                rs.getString("industry"),
                rs.getObject("founded_year") != null ? rs.getInt("founded_year") : null,
                rs.getInt("global_recognition"),
                rs.getInt("rank"),
                rs.getBoolean("last_fetched"),
                rs.getTimestamp("fetch_timestamp").toInstant()
        );
    }
}
