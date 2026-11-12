package io.clueterra.api.admin.hints.repository;

import java.util.List;

import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.api.admin.hints.dto.HintCountryOption;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class HintCountryJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final RowMapper<HintCountryOption> ROW_MAPPER = new DataClassRowMapper<>(HintCountryOption.class);

    public List<HintCountryOption> findCountriesWithHints(String search) {
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT c.id, c.iso_alpha2, c.name_en
                FROM countries c
                JOIN hints h ON h.country_id = c.id
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (search != null && !search.isBlank()) {
            sql.append(" WHERE LOWER(c.name_en) LIKE :search OR LOWER(c.iso_alpha2) LIKE :search");
            params.addValue("search", "%" + search.toLowerCase() + "%");
        }
        sql.append(" ORDER BY c.name_en");
        return jdbc.query(sql.toString(), params, ROW_MAPPER);
    }
}
