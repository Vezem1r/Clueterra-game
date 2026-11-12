package io.clueterra.api.silhouettegame.repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.api.common.utils.loader.SqlLoader;
import io.clueterra.api.silhouettegame.dto.SilhouetteMissDto;
import io.clueterra.api.silhouettegame.dto.SilhouettePlayerStatsDto;
import io.clueterra.api.silhouettegame.model.SilhouetteCountryRow;
import io.clueterra.api.silhouettegame.model.SilhouetteGuessRow;
import io.clueterra.api.silhouettegame.model.SilhouettePhotoRow;
import io.clueterra.api.silhouettegame.model.SilhouettePlayerSessionRow;
import io.clueterra.api.silhouettegame.model.SilhouetteQuestionRow;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class SilhouetteGameJdbcRepository {

    private static final String FIND_CURRENT = SqlLoader.load(SilhouetteGameJdbcRepository.class, "findCurrent");
    private static final String FIND_PLAYER_STATS = SqlLoader.load(SilhouetteGameJdbcRepository.class, "findPlayerStats");
    private static final String FIND_PLAYER_SESSION = "SELECT stats.*, ARRAY("
            + SqlLoader.load(SilhouetteGameJdbcRepository.class, "findSolved") + ") AS solved FROM (" + FIND_PLAYER_STATS + ") stats";
    private static final String FIND_COUNTRY = SqlLoader.load(SilhouetteGameJdbcRepository.class, "findCountry");
    private static final String FIND_DISCOVERED_COUNTRY = SqlLoader.load(SilhouetteGameJdbcRepository.class, "findDiscoveredCountry");
    private static final String FIND_ROUND_MISSES = SqlLoader.load(SilhouetteGameJdbcRepository.class, "findRoundMisses");
    private static final String FIND_COUNTRY_PHOTO_MIME_TYPE = SqlLoader.load(SilhouetteGameJdbcRepository.class, "findCountryPhotoMimeType");

    private final NamedParameterJdbcTemplate jdbc;

    public void selectNext(long userId) {
        jdbc.queryForObject("SELECT cl_api_schema.silhouette_game_next_question(:userId)", Map.of("userId", userId), Long.class);
    }

    public Optional<SilhouetteQuestionRow> findCurrent(long userId) {
        return jdbc.query(FIND_CURRENT, Map.of("userId", userId), (rs, row) -> new SilhouetteQuestionRow(
                rs.getLong("round_number"), rs.getInt("tier"), rs.getString("iso2"), rs.getString("shape_path"),
                rs.getString("hints"), rs.getInt("current_hints_used"), rs.getInt("current_attempts"),
                rs.getBoolean("already_rewarded"), rs.getInt("base_xp"),
                rs.getInt("hint_penalty_percent"), photo(rs))).stream().findFirst();
    }

    public void revealHint(long userId, long round) {
        jdbc.queryForObject("SELECT cl_api_schema.silhouette_game_reveal_hint(:userId, :round)",
                Map.of("userId", userId, "round", round), Short.class);
    }

    public SilhouetteGuessRow guess(long userId, long round, String countryCode) {
        return jdbc.queryForObject(
                "SELECT * FROM cl_api_schema.silhouette_game_guess(:userId, :round, :countryCode)",
                Map.of("userId", userId, "round", round, "countryCode", countryCode),
                (rs, row) -> new SilhouetteGuessRow(rs.getBoolean("correct"), rs.getInt("xp_awarded"),
                        rs.getInt("attempts"), rs.getLong("answer_question_id")));
    }

    public List<SilhouetteMissDto> findRoundMisses(long userId, long round) {
        return jdbc.query(FIND_ROUND_MISSES, Map.of("userId", userId, "round", round), (rs, row) -> new SilhouetteMissDto(
                rs.getString("iso2"), rs.getInt("distance_km"), rs.getInt("bearing_deg")));
    }

    public long showAnswer(long userId, long round) {
        Long questionId = jdbc.queryForObject("SELECT cl_api_schema.silhouette_game_show_answer(:userId, :round)",
                Map.of("userId", userId, "round", round), Long.class);
        return Optional.ofNullable(questionId)
                .orElseThrow(() -> new IllegalStateException("Silhouette answer returned no question"));
    }

    public void skip(long userId, long round) {
        jdbc.queryForList("SELECT cl_api_schema.silhouette_game_skip(:userId, :round)",
                Map.of("userId", userId, "round", round), Long.class);
    }

    public boolean claimGuest(String guestToken, long targetUserId) {
        Boolean claimed = jdbc.queryForObject(
                "SELECT cl_api_schema.silhouette_game_claim_guest(:guestToken, :targetUserId)",
                Map.of("guestToken", guestToken, "targetUserId", targetUserId), Boolean.class);
        return Boolean.TRUE.equals(claimed);
    }

    public SilhouettePlayerStatsDto findPlayerStats(long userId) {
        return jdbc.queryForObject(FIND_PLAYER_STATS, Map.of("userId", userId), (rs, row) -> stats(rs));
    }

    public SilhouettePlayerSessionRow findPlayerSession(long userId) {
        return jdbc.queryForObject(FIND_PLAYER_SESSION, Map.of("userId", userId),
                (rs, row) -> new SilhouettePlayerSessionRow(stats(rs), solved(rs.getArray("solved"))));
    }

    public long countCountries() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM cl_schema.silhouette_game_question WHERE is_active",
                Map.of(), Long.class);
        return count == null ? 0 : count;
    }

    public Optional<SilhouetteCountryRow> findCountry(long questionId) {
        return jdbc.query(FIND_COUNTRY, Map.of("questionId", questionId), (rs, row) -> country(rs)).stream().findFirst();
    }

    public Optional<SilhouetteCountryRow> findDiscoveredCountry(long userId, String iso2) {
        return jdbc.query(FIND_DISCOVERED_COUNTRY, Map.of("userId", userId, "iso2", iso2), (rs, row) -> country(rs))
                .stream().findFirst();
    }

    public Optional<String> findCountryPhotoMimeType(String objectKey) {
        return jdbc.queryForList(FIND_COUNTRY_PHOTO_MIME_TYPE, Map.of("objectKey", objectKey), String.class)
                .stream().findFirst();
    }

    private SilhouetteCountryRow country(ResultSet rs) throws SQLException {
        return new SilhouetteCountryRow(rs.getString("iso2"), rs.getString("subregion"), rs.getString("names"), rs.getString("facts"),
                rs.getString("flag_colors"), rs.getDouble("center_lng"), rs.getDouble("center_lat"),
                rs.getString("bounds"), rs.getString("shape_path"), photo(rs));
    }

    private SilhouettePhotoRow photo(ResultSet rs) throws SQLException {
        String objectKey = rs.getString("photo_object_key");
        return objectKey == null ? null : new SilhouettePhotoRow(objectKey, rs.getString("photo_author_name"),
                rs.getString("photo_license_label"), rs.getString("photo_license_url"),
                rs.getString("photo_source_page_url"), rs.getString("landmark_names"));
    }

    private SilhouettePlayerStatsDto stats(ResultSet rs) throws SQLException {
        return new SilhouettePlayerStatsDto(rs.getLong("attempted_countries"), rs.getLong("solved_countries"),
                rs.getLong("total_countries"), rs.getLong("answer_attempts"), rs.getLong("skips"), rs.getLong("xp_awarded"));
    }

    private List<String> solved(Array array) throws SQLException {
        return List.of((String[]) array.getArray());
    }
}
