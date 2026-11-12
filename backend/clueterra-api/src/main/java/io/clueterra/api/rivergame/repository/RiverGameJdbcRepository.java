package io.clueterra.api.rivergame.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.api.common.utils.loader.SqlLoader;
import io.clueterra.api.rivergame.dto.RiverPlayerStatsDto;
import io.clueterra.api.rivergame.model.RiverGuessRow;
import io.clueterra.api.rivergame.model.RiverQuestionRow;
import io.clueterra.api.rivergame.model.RiverRevealRow;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class RiverGameJdbcRepository {

    private static final String FIND_CURRENT = SqlLoader.load(RiverGameJdbcRepository.class, "findCurrent");
    private static final String SEARCH_NAMES = SqlLoader.load(RiverGameJdbcRepository.class, "searchNames");
    private static final String FIND_PLAYER_STATS = SqlLoader.load(RiverGameJdbcRepository.class, "findPlayerStats");
    private static final String FIND_REVEAL = SqlLoader.load(RiverGameJdbcRepository.class, "findReveal");
    private static final String FIND_RIVER_PHOTO = SqlLoader.load(RiverGameJdbcRepository.class, "findRiverPhoto");

    private final NamedParameterJdbcTemplate jdbc;

    public Long selectNext(long userId) {
        return jdbc.queryForObject("SELECT cl_api_schema.river_game_next_question(:userId)",
                Map.of("userId", userId), Long.class);
    }

    public Optional<RiverQuestionRow> findCurrent(long userId) {
        return jdbc.query(FIND_CURRENT, Map.of("userId", userId), (rs, row) -> new RiverQuestionRow(
                rs.getLong("id"), rs.getShort("rank_in_country"),
                rs.getString("plain_image_key"), rs.getString("borders_image_key"), rs.getString("course"),
                rs.getShort("current_hints_used"), rs.getInt("current_attempts"),
                rs.getLong("round_number"), rs.getBoolean("already_rewarded"),
                rs.getString("revealed_hints"), rs.getInt("base_xp"),
                rs.getInt("hint_penalty_percent"))).stream().findFirst();
    }

    public void revealHint(long userId, long questionId) {
        jdbc.queryForObject("SELECT cl_api_schema.river_game_reveal_hint(:userId, :questionId)",
                Map.of("userId", userId, "questionId", questionId), Short.class);
    }

    public RiverGuessRow guess(long userId, long questionId, String answer) {
        return jdbc.queryForObject("SELECT * FROM cl_api_schema.river_game_guess(:userId, :questionId, :answer)",
                Map.of("userId", userId, "questionId", questionId, "answer", answer),
                (rs, row) -> new RiverGuessRow(rs.getBoolean("correct"),
                        rs.getInt("xp_awarded"), rs.getInt("attempts")));
    }

    public void showAnswer(long userId, long questionId) {
        jdbc.queryForObject("SELECT cl_api_schema.river_game_show_answer(:userId, :questionId)",
                Map.of("userId", userId, "questionId", questionId), Long.class);
    }

    public Optional<RiverRevealRow> findReveal(long questionId) {
        return jdbc.query(FIND_REVEAL, Map.of("questionId", questionId), (rs, row) -> new RiverRevealRow(
                rs.getLong("id"), rs.getString("name_en"), rs.getString("country_code"),
                rs.getDouble("length_km"), rs.getString("borders_image_key"),
                rs.getString("facts_source_url"), rs.getString("facts"), rs.getString("wikipedia_url"),
                rs.getString("photo_object_key"), rs.getString("photo_author_name"),
                rs.getString("photo_license_label"), rs.getString("photo_license_url"),
                rs.getString("photo_source_page_url"))).stream().findFirst();
    }

    public Optional<String> findRiverPhotoMimeType(String objectKey) {
        return jdbc.queryForList(FIND_RIVER_PHOTO, Map.of("objectKey", objectKey), String.class)
                .stream().findFirst();
    }

    public Long skip(long userId, long questionId) {
        return jdbc.queryForObject("SELECT cl_api_schema.river_game_skip(:userId, :questionId)",
                Map.of("userId", userId, "questionId", questionId), Long.class);
    }

    public List<String> searchNames(String query) {
        return jdbc.queryForList(SEARCH_NAMES, Map.of("query", query), String.class);
    }

    public boolean claimGuest(String guestToken, long targetUserId) {
        Boolean claimed = jdbc.queryForObject(
                "SELECT cl_api_schema.river_game_claim_guest(:guestToken, :targetUserId)",
                Map.of("guestToken", guestToken, "targetUserId", targetUserId), Boolean.class);
        return Boolean.TRUE.equals(claimed);
    }

    public RiverPlayerStatsDto findPlayerStats(long userId) {
        return jdbc.queryForObject(FIND_PLAYER_STATS, Map.of("userId", userId), (rs, row) -> {
            long currentQuestionId = rs.getLong("current_question_id");
            Long currentId = rs.wasNull() ? null : currentQuestionId;
            return new RiverPlayerStatsDto(
                    rs.getLong("attempted_rivers"), rs.getLong("solved_rivers"),
                    rs.getLong("answer_attempts"), rs.getLong("skips"),
                    rs.getLong("xp_awarded"), currentId);
        });
    }
}
