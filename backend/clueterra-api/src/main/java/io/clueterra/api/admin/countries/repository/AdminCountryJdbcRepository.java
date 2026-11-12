package io.clueterra.api.admin.countries.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.api.admin.countries.dto.CountryAdminDto;
import io.clueterra.api.common.utils.database.QueryFilterBuilder;
import io.clueterra.api.common.utils.database.SortDirection;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AdminCountryJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String BASE_SELECT = """
            SELECT c.id, c.iso_alpha2, c.iso_alpha3, c.name_en, c.name_native,
                   c.region, c.subregion, c.capital, c.flag_emoji, c.flag_url,
                   c.difficulty_modifier, c.times_featured, c.times_guessed,
                   c.success_rate, c.is_active, c.created_at, c.updated_at
            FROM countries c
            """;

    private static final String BASE_COUNT = """
            SELECT COUNT(*) FROM countries c
            """;

    private static final RowMapper<CountryAdminDto> ROW_MAPPER =
            (rs, rowNum) -> new CountryAdminDto(
                    rs.getLong("id"),
                    rs.getString("iso_alpha2"),
                    rs.getString("iso_alpha3"),
                    rs.getString("name_en"),
                    rs.getString("name_native"),
                    rs.getString("region"),
                    rs.getString("subregion"),
                    rs.getString("capital"),
                    rs.getString("flag_emoji"),
                    rs.getString("flag_url"),
                    rs.getBigDecimal("difficulty_modifier"),
                    (Integer) rs.getObject("times_featured"),
                    (Integer) rs.getObject("times_guessed"),
                    rs.getBigDecimal("success_rate"),
                    rs.getBoolean("is_active"),
                    rs.getObject("created_at", LocalDateTime.class),
                    rs.getObject("updated_at", LocalDateTime.class),
                    null,
                    null);

    public Page<CountryAdminDto> findAll(String region, String search, Boolean active, String sortBy, String sortDir, Pageable pageable) {
        QueryFilterBuilder f = new QueryFilterBuilder()
                .bool("c.is_active", "active", active)
                .eq("c.region", "region", region)
                .ilike("LOWER(c.name_en) LIKE :search", "search", search);

        Long total = jdbc.queryForObject(BASE_COUNT + f.whereClause(), f.params(), Long.class);
        if (total == null || total == 0) return Page.empty(pageable);
        StringBuilder sql = new StringBuilder(BASE_SELECT).append(f.whereClause());

        sql.append(" ORDER BY ");
        if (sortBy != null) {
            switch (sortBy) {
                case "isoAlpha2" -> sql.append("c.iso_alpha2");
                case "region" -> sql.append("c.region");
                case "capital" -> sql.append("c.capital");
                case "subregion" -> sql.append("c.subregion");
                case "difficultyModifier" -> sql.append("c.difficulty_modifier");
                case "timesFeatured" -> sql.append("c.times_featured");
                case "successRate" -> sql.append("c.success_rate");
                case "isActive" -> sql.append("c.is_active");
                case "createdAt" -> sql.append("c.created_at");
                default -> sql.append("c.name_en");
            }
        } else {
            sql.append("c.name_en");
        }

        sql.append(" ").append(SortDirection.resolve(sortDir));

        if (pageable.isPaged()) {
            sql.append(" LIMIT :limit OFFSET :offset");
            f.params().addValue("limit", pageable.getPageSize());
            f.params().addValue("offset", pageable.getOffset());
        }
        return new PageImpl<>(jdbc.query(sql.toString(), f.params(), ROW_MAPPER), pageable, total);
    }

    public Optional<CountryAdminDto> findById(Long id) {
        return jdbc.query(BASE_SELECT + " WHERE c.id = :id", new MapSqlParameterSource("id", id), ROW_MAPPER).stream().findFirst();
    }

    public Optional<CountryAdminDto> update(Long id, String nameEn, String nameNative, String capital, BigDecimal difficultyModifier, Boolean isActive) {
        StringBuilder sql = new StringBuilder("UPDATE countries SET ");
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        boolean hasUpdates = false;

        if (nameEn != null) {
            sql.append("name_en = :nameEn");
            params.addValue("nameEn", nameEn);
            hasUpdates = true;
        }
        if (nameNative != null) {
            if (hasUpdates) sql.append(", ");
            sql.append("name_native = :nameNative");
            params.addValue("nameNative", nameNative);
            hasUpdates = true;
        }
        if (capital != null) {
            if (hasUpdates) sql.append(", ");
            sql.append("capital = :capital");
            params.addValue("capital", capital);
            hasUpdates = true;
        }
        if (difficultyModifier != null) {
            if (hasUpdates) sql.append(", ");
            sql.append("difficulty_modifier = :difficultyMod");
            params.addValue("difficultyMod", difficultyModifier);
            hasUpdates = true;
        }
        if (isActive != null) {
            if (hasUpdates) sql.append(", ");
            sql.append("is_active = :isActive");
            params.addValue("isActive", isActive);
            hasUpdates = true;
        }

        if (!hasUpdates) return findById(id);

        sql.append(", updated_at = :updatedAt WHERE id = :id RETURNING *");
        params.addValue("updatedAt", LocalDateTime.now());
        return jdbc.query(sql.toString(), params, ROW_MAPPER).stream().findFirst();
    }

    public Optional<CountryAdminDto> setActive(Long id, boolean active) {
        var params = new MapSqlParameterSource().addValue("id", id).addValue("active", active).addValue("now", LocalDateTime.now());
        return jdbc.query("UPDATE countries SET is_active = :active, updated_at = :now WHERE id = :id RETURNING *", params, ROW_MAPPER).stream().findFirst();
    }

    public List<String> findAllRegions() {
        return jdbc.queryForList("SELECT DISTINCT region FROM countries WHERE region IS NOT NULL ORDER BY region", new MapSqlParameterSource(), String.class);
    }

    public long countHintsByCountryId(Long countryId) {
        Long r = jdbc.queryForObject("SELECT COUNT(*) FROM hints WHERE country_id = :cid", new MapSqlParameterSource("cid", countryId), Long.class);
        return r != null ? r : 0;
    }

    public long countActiveHintsByCountryId(Long countryId) {
        Long r = jdbc.queryForObject("SELECT COUNT(*) FROM hints WHERE country_id = :cid AND is_active = true", new MapSqlParameterSource("cid", countryId), Long.class);
        return r != null ? r : 0;
    }
}
