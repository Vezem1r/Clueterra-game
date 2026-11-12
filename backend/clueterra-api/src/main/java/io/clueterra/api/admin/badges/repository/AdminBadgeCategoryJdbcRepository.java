package io.clueterra.api.admin.badges.repository;

import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import io.clueterra.api.admin.badges.dto.BadgeCategoryDto;
import io.hypersistence.tsid.TSID;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AdminBadgeCategoryJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String FIND_ALL = """
            SELECT bc.id, bc.name, bc.display_order,
                   COALESCE(cnt.badge_count, 0) AS badge_count
            FROM badge_categories bc
            LEFT JOIN (SELECT category_id, COUNT(*) AS badge_count FROM badges GROUP BY category_id) cnt
                ON cnt.category_id = bc.id
            ORDER BY bc.display_order , bc.created_at
            """;

    public List<BadgeCategoryDto> findAll() {
        return jdbc.query(FIND_ALL, (rs, rowNum) -> new BadgeCategoryDto(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getInt("display_order"),
                rs.getInt("badge_count")
        ));
    }

    public BadgeCategoryDto insert(String name) {
        Long id = TSID.Factory.getTsid().toLong();
        Integer maxOrder = jdbc.queryForObject(
                "SELECT COALESCE(MAX(display_order), 0) FROM badge_categories",
                new MapSqlParameterSource(), Integer.class);
        int displayOrder = (maxOrder != null ? maxOrder : 0) + 1;

        jdbc.update("""
                INSERT INTO badge_categories (id, name, display_order)
                VALUES (:id, :name, :displayOrder)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", name)
                .addValue("displayOrder", displayOrder));
        return new BadgeCategoryDto(id, name, displayOrder, 0);
    }

    public int rename(Long id, String name) {
        return jdbc.update("UPDATE badge_categories SET name = :name, updated_at = NOW() WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("name", name));
    }

    public boolean hasAssignedBadges(Long id) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM badges WHERE category_id = :id",
                new MapSqlParameterSource("id", id), Long.class);
        return count != null && count > 0;
    }

    public void delete(Long id) {
        jdbc.update("DELETE FROM badge_categories WHERE id = :id", new MapSqlParameterSource("id", id));
    }

    public void bulkUpdateCategoryOrder(List<Long> orderedIds) {
        if (orderedIds.isEmpty()) return;
        SqlParameterSource[] batch = new SqlParameterSource[orderedIds.size()];
        for (int i = 0; i < orderedIds.size(); i++) {
            batch[i] = new MapSqlParameterSource()
                    .addValue("id", orderedIds.get(i))
                    .addValue("pos", i + 1);
        }
        jdbc.batchUpdate(
                "UPDATE badge_categories SET display_order = :pos, updated_at = NOW() WHERE id = :id", batch);
    }
}
