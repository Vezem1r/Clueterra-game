package io.clueterra.questions.clients.openstreetmap.dao;

import io.clueterra.questions.clients.openstreetmap.dto.OsmCountryStatistic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.sql.SQLException;

@Slf4j
@Repository
@RequiredArgsConstructor
public class OsmCountryStatisticDao {

    private static final int BATCH_SIZE = 1000;

    private static final String UPSERT_SQL = """
            INSERT INTO cl_staging.osm_country_statistics
                (country_code, indicator_code, category, value, value_text, weight, fetched_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (country_code, indicator_code)
            DO UPDATE SET
                value = EXCLUDED.value,
                value_text = EXCLUDED.value_text,
                weight = EXCLUDED.weight,
                fetched_at = EXCLUDED.fetched_at,
                category = EXCLUDED.category
            WHERE
                osm_country_statistics.value IS DISTINCT FROM EXCLUDED.value
                OR osm_country_statistics.value_text IS DISTINCT FROM EXCLUDED.value_text
                OR osm_country_statistics.weight IS DISTINCT FROM EXCLUDED.weight
                OR osm_country_statistics.category IS DISTINCT FROM EXCLUDED.category;
            """;

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void saveAll(List<OsmCountryStatistic> statistics) {
        if (statistics.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(UPSERT_SQL, statistics, BATCH_SIZE, this::setParameters);
    }

    private void setParameters(PreparedStatement ps, OsmCountryStatistic stat) throws SQLException {
        ps.setString(1, stat.getCountryCode());
        ps.setString(2, stat.getIndicatorCode());
        ps.setString(3, stat.getCategory().name());

        if (stat.getValue() != null) {
            ps.setBigDecimal(4, stat.getValue());
        } else {
            ps.setNull(4, Types.NUMERIC);
        }

        ps.setString(5, stat.getValueText());
        ps.setBigDecimal(6, stat.getWeight());
        ps.setTimestamp(7, Timestamp.from(stat.getFetchedAt()));
    }

    @Transactional
    public void syncCountryToMain(String countryCode) {
        jdbcTemplate.execute(
                "SELECT sync_osm_stats_to_main(?)",
                (PreparedStatementCallback<Boolean>) ps -> {
                    ps.setString(1, countryCode);
                    return ps.execute();
                }
        );
        log.debug("Synced OSM records for {} to main table", countryCode);
    }

    @Transactional
    public void syncAllToMain() {
        jdbcTemplate.execute("SELECT sync_osm_stats_to_main(NULL)");
        log.debug("Synced all OSM records to main table");
    }
}
