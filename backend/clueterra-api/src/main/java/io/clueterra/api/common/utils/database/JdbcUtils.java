package io.clueterra.api.common.utils.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JdbcUtils {

    private JdbcUtils() {
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {
    };

    public static Map<String, Object> jsonbMap(ResultSet rs, String col) throws SQLException {
        String json = rs.getString(col);
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return JSON.readValue(json, MAP_REF);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    public static String ts(ResultSet rs, String col) throws SQLException {
        var t = rs.getTimestamp(col);
        return t != null ? t.toLocalDateTime().toString() : null;
    }

    public static Long nullLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    public static <T> Page<T> paginate(NamedParameterJdbcTemplate jdbc,
                                        String countSql, String selectSql, String whereClause,
                                        MapSqlParameterSource params, String sortBy, String sortDir,
                                        Set<String> allowedSort, Map<String, String> sortMap,
                                        String defaultSort, Pageable pageable, RowMapper<T> mapper) {
        Long total = jdbc.queryForObject(countSql + whereClause, params, Long.class);
        if (total == null || total == 0) return Page.empty(pageable);

        String col = allowedSort.contains(sortBy) ? sortMap.getOrDefault(sortBy, defaultSort) : defaultSort;
        String dir = SortDirection.resolve(sortDir);

        String sql = selectSql + whereClause
                + " ORDER BY " + col + " " + dir
                + " LIMIT :limit OFFSET :offset";
        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());

        List<T> content = jdbc.query(sql, params, mapper);
        return new PageImpl<>(content, pageable, total);
    }
}
