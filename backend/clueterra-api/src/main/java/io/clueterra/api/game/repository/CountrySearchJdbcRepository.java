package io.clueterra.api.game.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.api.game.dto.CountryDto;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CountrySearchJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final int SEARCH_LIMIT = 10;
    private static final double SIMILARITY_THRESHOLD = 0.25;

    private static final String SEARCH_COUNTRIES = """
            SELECT id, iso_alpha2, name_en, flag_emoji, flag_url, region
            FROM countries
            WHERE is_active = true
              AND (LOWER(name_en) LIKE LOWER('%' || :q || '%')
                   OR LOWER(iso_alpha2) = LOWER(:q)
                   OR LOWER(iso_alpha3) = LOWER(:q)
                   OR similarity(name_en, :q) > :threshold)
            ORDER BY
              CASE WHEN LOWER(name_en) LIKE LOWER(:q || '%') THEN 0
                   WHEN LOWER(name_en) LIKE LOWER('%' || :q || '%') THEN 1
                   ELSE 2 END,
              similarity(name_en, :q) DESC
            LIMIT :limit
            """;

    private static final String FIND_BY_CODE = """
            SELECT id, iso_alpha2, name_en, flag_emoji, flag_url, region
            FROM countries
            WHERE LOWER(iso_alpha2) = LOWER(:code)
            """;

    private static final RowMapper<CountryDto> ROW_MAPPER =
            (rs, rowNum) -> new CountryDto(
                    rs.getLong("id"),
                    rs.getString("iso_alpha2"),
                    rs.getString("name_en"),
                    rs.getString("flag_emoji"),
                    rs.getString("flag_url"),
                    rs.getString("region"));

    public List<CountryDto> search(String query) {
        var params = new MapSqlParameterSource()
                .addValue("q", query)
                .addValue("limit", SEARCH_LIMIT)
                .addValue("threshold", SIMILARITY_THRESHOLD);
        return jdbc.query(SEARCH_COUNTRIES, params, ROW_MAPPER);
    }

    public Optional<CountryDto> findByCode(String code) {
        var params = new MapSqlParameterSource("code", code);
        return jdbc.query(FIND_BY_CODE, params, ROW_MAPPER).stream().findFirst();
    }
}
