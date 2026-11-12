package io.clueterra.api.leaderboard.repository;

import java.util.List;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.api.common.utils.loader.SqlLoader;
import io.clueterra.api.leaderboard.dto.LeaderboardEntryDto;
import io.clueterra.api.leaderboard.dto.LeaderboardPageResponse;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class LeaderboardJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String SELECT_LEADERBOARD = SqlLoader.load(LeaderboardJdbcRepository.class, "findPage");

    private static final RowMapper<LeaderboardEntryDto> ROW_MAPPER =
            (rs, rowNum) -> new LeaderboardEntryDto(
                    rs.getLong("user_id"),
                    rs.getString("username"),
                    rs.getString("display_name"),
                    rs.getString("avatar_url"),
                    rs.getLong("total_xp"),
                    rs.getInt("current_level"),
                    rs.getInt("challenges_won"),
                    rs.getInt("rank"));

    public LeaderboardPageResponse findPage(int page, int size, int offset) {
        var params = new MapSqlParameterSource().addValue("limit", size).addValue("offset", offset);

        long[] totalHolder = {0};
        List<LeaderboardEntryDto> entries = jdbc.query(SELECT_LEADERBOARD, params, (rs, rowNum) -> {
            totalHolder[0] = rs.getLong("total_count");
            return ROW_MAPPER.mapRow(rs, rowNum);
        });

        long totalElements = entries.isEmpty() ? 0 : totalHolder[0];
        int totalPages = (int) Math.ceil((double) totalElements / size);

        return new LeaderboardPageResponse(entries, page, size, totalElements, totalPages);
    }
}
