package io.clueterra.api.game.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.api.common.enums.SessionStatus;
import io.clueterra.api.common.utils.loader.SqlLoader;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ChallengeJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String SELECT_BY_DATE_AND_LEVEL = """
            SELECT id, challenge_date, challenge_level, difficulty, total_countries,
                   total_hints, countries_json, hints_json
            FROM daily_challenges
            WHERE challenge_date = :today AND challenge_level = :level AND is_active = true
            """;

    private static final RowMapper<ChallengeRow> ROW_MAPPER =
            (rs, rowNum) -> new ChallengeRow(
                    rs.getLong("id"),
                    rs.getObject("challenge_date", LocalDate.class),
                    rs.getString("challenge_level"),
                    rs.getString("difficulty"),
                    rs.getInt("total_countries"),
                    rs.getInt("total_hints"),
                    rs.getString("countries_json"),
                    rs.getString("hints_json"));

    private static final String FIND_ACTIVE_SUMMARIES_BY_DATE =
            SqlLoader.load(ChallengeJdbcRepository.class, "findActiveSummariesByDate");

    private static final String FIND_SUMMARY_BY_DATE_AND_LEVEL =
            SqlLoader.load(ChallengeJdbcRepository.class, "findSummaryByDateAndLevel");

    private static final RowMapper<ChallengeSummaryRow> SUMMARY_ROW_MAPPER =
            (rs, rowNum) -> new ChallengeSummaryRow(
                    rs.getLong("id"),
                    rs.getObject("challenge_date", LocalDate.class),
                    rs.getString("challenge_level"),
                    rs.getString("difficulty"),
                    rs.getInt("total_countries"),
                    rs.getInt("total_hints"),
                    rs.getString("countries_json"),
                    rs.getString("hints_json"),
                    rs.getInt("continent_count"),
                    rs.getObject("avg_xp") != null ? rs.getInt("avg_xp") : null);

    public Optional<ChallengeRow> findByDateAndLevel(LocalDate date, String level) {
        var params = new MapSqlParameterSource()
                .addValue("today", date)
                .addValue("level", level);
        return jdbc.query(SELECT_BY_DATE_AND_LEVEL, params, ROW_MAPPER).stream().findFirst();
    }

    public List<ChallengeSummaryRow> findActiveSummariesByDate(LocalDate date) {
        var params = new MapSqlParameterSource()
                .addValue("today", date)
                .addValue("completedStatus", SessionStatus.COMPLETED.name());
        return jdbc.query(FIND_ACTIVE_SUMMARIES_BY_DATE, params, SUMMARY_ROW_MAPPER);
    }

    public Optional<ChallengeSummaryRow> findSummaryByDateAndLevel(LocalDate date, String level) {
        var params = new MapSqlParameterSource()
                .addValue("today", date)
                .addValue("level", level)
                .addValue("completedStatus", SessionStatus.COMPLETED.name());
        return jdbc.query(FIND_SUMMARY_BY_DATE_AND_LEVEL, params, SUMMARY_ROW_MAPPER).stream().findFirst();
    }

    public record ChallengeRow(Long id, LocalDate challengeDate, String challengeLevel, String difficulty,
                               int totalCountries, int totalHints, String countriesJson, String hintsJson) {
    }

    public record ChallengeSummaryRow(
            Long id,
            LocalDate challengeDate,
            String challengeLevel,
            String difficulty,
            int totalCountries,
            int totalHints,
            String countriesJson,
            String hintsJson,
            int continentCount,
            Integer avgXp) {
    }
}
