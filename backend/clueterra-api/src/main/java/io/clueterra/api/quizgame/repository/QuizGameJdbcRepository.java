package io.clueterra.api.quizgame.repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.api.common.utils.loader.SqlLoader;
import io.clueterra.api.quizgame.dto.QuizCountryDto;
import io.clueterra.api.quizgame.dto.QuizPlayerStatsDto;
import io.clueterra.api.quizgame.dto.QuizCountryLevelDto;
import io.clueterra.api.quizgame.dto.QuizVisitDto;
import io.clueterra.api.quizgame.model.QuizAnswerOutcome;
import io.clueterra.api.quizgame.model.QuizStopRow;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class QuizGameJdbcRepository {

    private static final String FIND_STOP = SqlLoader.load(QuizGameJdbcRepository.class, "findStop");
    private static final String FIND_COUNTRIES = SqlLoader.load(QuizGameJdbcRepository.class, "findCountries");
    private static final String FIND_TRAIL = SqlLoader.load(QuizGameJdbcRepository.class, "findTrail");
    private static final String FIND_PLAYER_STATS = SqlLoader.load(QuizGameJdbcRepository.class, "findPlayerStats");
    private static final String FIND_OPTION_FLAG = SqlLoader.load(QuizGameJdbcRepository.class, "findOptionFlag");

    private static final long NO_PLAYER = 0L;

    private final NamedParameterJdbcTemplate jdbc;

    public void start(long userId) {
        jdbc.queryForObject("SELECT cl_api_schema.quiz_game_start(:userId)", Map.of("userId", userId), Long.class);
    }

    public void travel(long userId, long stop) {
        jdbc.queryForObject("SELECT cl_api_schema.quiz_game_travel(:userId, :stop)",
                Map.of("userId", userId, "stop", stop), Long.class);
    }

    public QuizAnswerOutcome answer(long userId, long stop, int position, int option) {
        return jdbc.queryForObject(
                "SELECT * FROM cl_api_schema.quiz_game_answer(:userId, :stop, :position, :option)",
                Map.of("userId", userId, "stop", stop, "position", position, "option", option),
                (rs, row) -> new QuizAnswerOutcome(rs.getBoolean("correct"), rs.getInt("answer_index"),
                        rs.getInt("xp_awarded"), rs.getBoolean("explored")));
    }

    public Optional<QuizStopRow> findStop(long userId) {
        return jdbc.query(FIND_STOP, Map.of("userId", userId), (rs, row) -> new QuizStopRow(
                rs.getLong("stop_number"),
                new QuizCountryDto(rs.getString("iso2"), rs.getString("names"), rs.getString("region"),
                        rs.getString("subregion"), rs.getInt("tier")),
                rs.getInt("current_level"),
                rs.getInt("levels"),
                rs.getString("arrival_mode"),
                rs.getString("from_iso2"),
                destination(rs),
                rs.getString("next_mode"),
                route(rs, "next_route"),
                rs.getInt("next_level"),
                rs.getString("questions"),
                rs.getInt("answer_xp"))).stream().findFirst();
    }

    public List<QuizCountryLevelDto> findCountries(long userId) {
        return jdbc.query(FIND_COUNTRIES, Map.of("userId", userId), (rs, row) -> new QuizCountryLevelDto(
                rs.getString("iso2"), rs.getInt("level"), rs.getInt("levels")));
    }

    public List<QuizVisitDto> findTrail(long userId, int limit) {
        return jdbc.query(FIND_TRAIL, Map.of("userId", userId, "limit", limit), (rs, row) -> new QuizVisitDto(
                rs.getLong("stop_number"), rs.getString("iso2"), rs.getString("from_iso2"), rs.getString("arrival_mode"),
                route(rs, "route"), rs.getInt("level")));
    }

    public QuizPlayerStatsDto findPlayerStats(long userId) {
        return jdbc.queryForObject(FIND_PLAYER_STATS, Map.of("userId", userId), (rs, row) -> new QuizPlayerStatsDto(
                rs.getLong("explored_countries"), rs.getLong("mastered_countries"), rs.getLong("total_countries"),
                rs.getLong("completed_levels"), rs.getLong("total_levels"), rs.getLong("answered_questions"),
                rs.getLong("correct_answers"), rs.getLong("xp_awarded")));
    }

    public QuizPlayerStatsDto emptyStats() {
        return findPlayerStats(NO_PLAYER);
    }

    public Optional<String> findOptionFlag(long userId, long stop, int position, int option) {
        return jdbc.queryForList(FIND_OPTION_FLAG,
                        Map.of("userId", userId, "stop", stop, "position", position, "option", option), String.class)
                .stream().findFirst();
    }

    public boolean claimGuest(String guestToken, long targetUserId) {
        Boolean claimed = jdbc.queryForObject(
                "SELECT cl_api_schema.quiz_game_claim_guest(:guestToken, :targetUserId)",
                Map.of("guestToken", guestToken, "targetUserId", targetUserId), Boolean.class);
        return Boolean.TRUE.equals(claimed);
    }

    private QuizCountryDto destination(ResultSet rs) throws SQLException {
        String iso2 = rs.getString("next_iso2");
        return iso2 == null ? null : new QuizCountryDto(iso2, rs.getString("next_names"), rs.getString("next_region"),
                rs.getString("next_subregion"), rs.getInt("next_tier"));
    }

    private List<String> route(ResultSet rs, String column) throws SQLException {
        Array route = rs.getArray(column);
        return route == null ? List.of() : List.of((String[]) route.getArray());
    }
}
