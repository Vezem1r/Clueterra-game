package io.clueterra.api.flaggame.repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.api.common.utils.loader.SqlLoader;
import io.clueterra.api.flaggame.dto.FlagPlayerStatsDto;
import io.clueterra.api.flaggame.model.FlagCountryRow;
import io.clueterra.api.flaggame.model.FlagGuessRow;
import io.clueterra.api.flaggame.model.FlagPlayerSessionRow;
import io.clueterra.api.flaggame.model.FlagQuestionRow;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class FlagGameJdbcRepository {

    private static final String FIND_CURRENT = SqlLoader.load(FlagGameJdbcRepository.class, "findCurrent");
    private static final String FIND_PLAYER_STATS = SqlLoader.load(FlagGameJdbcRepository.class, "findPlayerStats");
    private static final String FIND_PLAYER_SESSION = "SELECT stats.*, ARRAY("
            + SqlLoader.load(FlagGameJdbcRepository.class, "findSolved") + ") AS solved FROM (" + FIND_PLAYER_STATS + ") stats";
    private static final String FIND_COUNTRY = SqlLoader.load(FlagGameJdbcRepository.class, "findCountry");
    private static final String FIND_DISCOVERED_COUNTRY = SqlLoader.load(FlagGameJdbcRepository.class, "findDiscoveredCountry");
    private static final String FIND_ROUND_MISSES = SqlLoader.load(FlagGameJdbcRepository.class, "findRoundMisses");
    private static final String FIND_ROUND_FLAG = SqlLoader.load(FlagGameJdbcRepository.class, "findRoundFlag");

    private final NamedParameterJdbcTemplate jdbc;

    public void selectNext(long userId) {
        jdbc.queryForObject("SELECT cl_api_schema.flag_game_next_question(:userId)", Map.of("userId", userId), Long.class);
    }

    public Optional<FlagQuestionRow> findCurrent(long userId) {
        return jdbc.query(FIND_CURRENT, Map.of("userId", userId), (rs, row) -> new FlagQuestionRow(
                rs.getLong("round_number"), rs.getInt("tier"), rs.getString("iso2"), rs.getString("hints"),
                rs.getInt("current_hints_used"), rs.getInt("current_attempts"), rs.getBoolean("already_rewarded"),
                rs.getInt("base_xp"), rs.getInt("hint_penalty_percent"))).stream().findFirst();
    }

    public Optional<String> findRoundFlag(long userId, long round) {
        return jdbc.queryForList(FIND_ROUND_FLAG, Map.of("userId", userId, "round", round), String.class)
                .stream().findFirst();
    }

    public void revealHint(long userId, long round) {
        jdbc.queryForObject("SELECT cl_api_schema.flag_game_reveal_hint(:userId, :round)",
                Map.of("userId", userId, "round", round), Short.class);
    }

    public FlagGuessRow guess(long userId, long round, String countryCode) {
        return jdbc.queryForObject(
                "SELECT * FROM cl_api_schema.flag_game_guess(:userId, :round, :countryCode)",
                Map.of("userId", userId, "round", round, "countryCode", countryCode),
                (rs, row) -> new FlagGuessRow(rs.getBoolean("correct"), rs.getInt("xp_awarded"),
                        rs.getInt("attempts"), rs.getLong("answer_question_id")));
    }

    public List<String> findRoundMisses(long userId, long round) {
        return jdbc.queryForList(FIND_ROUND_MISSES, Map.of("userId", userId, "round", round), String.class);
    }

    public long showAnswer(long userId, long round) {
        Long questionId = jdbc.queryForObject("SELECT cl_api_schema.flag_game_show_answer(:userId, :round)",
                Map.of("userId", userId, "round", round), Long.class);
        return Optional.ofNullable(questionId)
                .orElseThrow(() -> new IllegalStateException("Flag answer returned no question"));
    }

    public void skip(long userId, long round) {
        jdbc.queryForList("SELECT cl_api_schema.flag_game_skip(:userId, :round)",
                Map.of("userId", userId, "round", round), Long.class);
    }

    public boolean claimGuest(String guestToken, long targetUserId) {
        Boolean claimed = jdbc.queryForObject(
                "SELECT cl_api_schema.flag_game_claim_guest(:guestToken, :targetUserId)",
                Map.of("guestToken", guestToken, "targetUserId", targetUserId), Boolean.class);
        return Boolean.TRUE.equals(claimed);
    }

    public FlagPlayerStatsDto findPlayerStats(long userId) {
        return jdbc.queryForObject(FIND_PLAYER_STATS, Map.of("userId", userId), (rs, row) -> stats(rs));
    }

    public FlagPlayerSessionRow findPlayerSession(long userId) {
        return jdbc.queryForObject(FIND_PLAYER_SESSION, Map.of("userId", userId),
                (rs, row) -> new FlagPlayerSessionRow(stats(rs), solved(rs.getArray("solved"))));
    }

    public long countFlags() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM cl_schema.flag_game_question WHERE is_active",
                Map.of(), Long.class);
        return count == null ? 0 : count;
    }

    public Optional<FlagCountryRow> findCountry(long questionId) {
        return jdbc.query(FIND_COUNTRY, Map.of("questionId", questionId), (rs, row) -> country(rs)).stream().findFirst();
    }

    public Optional<FlagCountryRow> findDiscoveredCountry(long userId, String iso2) {
        return jdbc.query(FIND_DISCOVERED_COUNTRY, Map.of("userId", userId, "iso2", iso2), (rs, row) -> country(rs))
                .stream().findFirst();
    }

    private FlagCountryRow country(ResultSet rs) throws SQLException {
        return new FlagCountryRow(rs.getString("iso2"), rs.getString("region"), rs.getString("subregion"),
                rs.getString("names"), rs.getString("capitals"));
    }

    private FlagPlayerStatsDto stats(ResultSet rs) throws SQLException {
        return new FlagPlayerStatsDto(rs.getLong("attempted_flags"), rs.getLong("solved_flags"),
                rs.getLong("total_flags"), rs.getLong("answer_attempts"), rs.getLong("skips"), rs.getLong("xp_awarded"));
    }

    private List<String> solved(Array array) throws SQLException {
        return List.of((String[]) array.getArray());
    }
}
