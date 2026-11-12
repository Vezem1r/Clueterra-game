package io.clueterra.api.admin.challenges.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.api.admin.challenges.dto.ChallengeAdminDto;
import io.clueterra.api.common.utils.database.QueryFilterBuilder;
import io.clueterra.api.common.utils.database.SortDirection;
import io.clueterra.api.common.utils.loader.SqlLoader;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AdminChallengeJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String BASE_SELECT = """
            SELECT id, challenge_date, challenge_level, difficulty,
                   total_countries, total_hints, is_active, created_at
            FROM daily_challenges
            """;

    private static final String BASE_COUNT = "SELECT COUNT(*) FROM daily_challenges ";

    private static final String DETAIL_SELECT = """
            SELECT id, challenge_date, challenge_level, difficulty,
                   total_countries, total_hints, is_active, created_at,
                   countries_json, hints_json
            FROM daily_challenges WHERE id = :id
            """;

    private static final Set<String> ALLOWED_SORT = Set.of(
            "challengeDate", "challengeLevel", "difficulty", "totalCountries",
            "totalHints", "isActive", "createdAt");

    private static final Map<String, String> SORT_MAP = Map.of(
            "challengeDate", "challenge_date",
            "challengeLevel", "challenge_level",
            "difficulty", "difficulty",
            "totalCountries", "total_countries",
            "totalHints", "total_hints",
            "isActive", "is_active",
            "createdAt", "created_at");

    private static final RowMapper<ChallengeAdminDto> LIST_MAPPER = (rs, rowNum) -> mapRow(rs, false);
    private static final RowMapper<ChallengeAdminDto> DETAIL_MAPPER = (rs, rowNum) -> mapRow(rs, true);

    private static final String SET_ACTIVE_SQL = SqlLoader.load(AdminChallengeJdbcRepository.class, "setActive");
    private static final String UPDATE_SQL = SqlLoader.load(AdminChallengeJdbcRepository.class, "update");

    private static ChallengeAdminDto mapRow(ResultSet rs, boolean includeJson) throws SQLException {
        return new ChallengeAdminDto(
                rs.getLong("id"),
                rs.getDate("challenge_date").toLocalDate(),
                rs.getString("challenge_level"),
                rs.getString("difficulty"),
                rs.getInt("total_countries"),
                rs.getInt("total_hints"),
                rs.getBoolean("is_active"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                false,
                false,
                includeJson ? rs.getString("countries_json") : null,
                includeJson ? rs.getString("hints_json") : null);
    }

    public Page<ChallengeAdminDto> findAll(LocalDate startDate, LocalDate endDate,
                                           String level, String challengeDifficulty, Boolean active,
                                           String sortBy, String sortDir, Pageable pageable,
                                           LocalDate excludeDate) {
        QueryFilterBuilder f = new QueryFilterBuilder()
                .gteDate("challenge_date", "startDate", startDate)
                .lteDate("challenge_date", "endDate", endDate)
                .eq("challenge_level", "level", level)
                .eq("difficulty", "difficulty", challengeDifficulty)
                .bool("is_active", "active", active)
                .neqDate("challenge_date", "excludeDate", excludeDate);

        Long total = jdbc.queryForObject(BASE_COUNT + f.whereClause(), f.params(), Long.class);
        if (total == null || total == 0) return Page.empty(pageable);

        String col = ALLOWED_SORT.contains(sortBy) ? SORT_MAP.getOrDefault(sortBy, "challenge_date") : "challenge_date";
        String dir = SortDirection.resolve(sortDir);

        String sql = BASE_SELECT + f.whereClause()
                + " ORDER BY " + col + " " + dir
                + " LIMIT :limit OFFSET :offset";
        f.params().addValue("limit", pageable.getPageSize());
        f.params().addValue("offset", pageable.getOffset());

        return new PageImpl<>(jdbc.query(sql, f.params(), LIST_MAPPER), pageable, total);
    }

    public List<ChallengeAdminDto> findByDate(LocalDate date, String level, String challengeDifficulty, Boolean active) {
        StringBuilder where = new StringBuilder(" WHERE challenge_date = :date");
        MapSqlParameterSource params = new MapSqlParameterSource("date", date);

        if (level != null && !level.isBlank()) {
            where.append(" AND challenge_level = :level");
            params.addValue("level", level);
        }
        if (challengeDifficulty != null && !challengeDifficulty.isBlank()) {
            where.append(" AND difficulty = :difficulty");
            params.addValue("difficulty", challengeDifficulty);
        }
        if (active != null) {
            where.append(" AND is_active = :active");
            params.addValue("active", active);
        }

        return jdbc.query(BASE_SELECT + where + " ORDER BY challenge_level", params, LIST_MAPPER);
    }

    public Optional<ChallengeAdminDto> findById(Long id) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        List<ChallengeAdminDto> list = jdbc.query(DETAIL_SELECT, params, DETAIL_MAPPER);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    public Optional<ChallengeAdminDto> setActive(Long id, boolean active) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", id).addValue("active", active);
        return jdbc.query(SET_ACTIVE_SQL, params, DETAIL_MAPPER).stream().findFirst();
    }

    public Optional<ChallengeAdminDto> update(Long id, String challengeLevel, String difficulty) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", id).addValue("level", challengeLevel).addValue("difficulty", difficulty);
        return jdbc.query(UPDATE_SQL, params, DETAIL_MAPPER).stream().findFirst();
    }

    public long count() {
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM daily_challenges", Map.of(), Long.class);
        return c != null ? c : 0;
    }

    public long countByIsActive(boolean active) {
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM daily_challenges WHERE is_active = :a", Map.of("a", active), Long.class);
        return c != null ? c : 0;
    }

    public List<String> findDistinctLevels() {
        return jdbc.queryForList("SELECT DISTINCT challenge_level FROM daily_challenges ORDER BY challenge_level", Map.of(), String.class);
    }

    public List<String> findDistinctDifficulties() {
        return jdbc.queryForList("SELECT DISTINCT difficulty FROM daily_challenges ORDER BY difficulty", Map.of(), String.class);
    }
}
