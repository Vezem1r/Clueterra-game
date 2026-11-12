package io.clueterra.questions.clients.budgetprojects;

import io.clueterra.questions.repository.util.SqlLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class BudgetProjectRepository {

    private static final String FIND_PROJECTS = SqlLoader.load(BudgetProjectRepository.class, "findProjects");
    private final NamedParameterJdbcTemplate jdbc;

    public boolean isAvailable() {
        Boolean available = jdbc.queryForObject(
                "SELECT to_regclass('cl_budget_schema.project') IS NOT NULL "
                        + "AND to_regclass('cl_budget_schema.project_current_lifecycle') IS NOT NULL "
                        + "AND to_regclass('cl_budget_schema.project_image_projection') IS NOT NULL "
                        + "AND to_regclass('cl_budget_schema.project_cost') IS NOT NULL "
                        + "AND to_regclass('cl_budget_schema.project_metric') IS NOT NULL "
                        + "AND to_regclass('cl_budget_schema.project_schedule_milestone') IS NOT NULL "
                        + "AND to_regclass('cl_media_schema.image_resource') IS NOT NULL "
                        + "AND to_regclass('cl_media_schema.image_rendition') IS NOT NULL",
                Map.of(), Boolean.class);
        return Boolean.TRUE.equals(available);
    }

    public List<BudgetProjectSnapshot> findProjects(String countryCode) {
        return jdbc.query(FIND_PROJECTS, Map.of("countryCode", countryCode == null ? "" : countryCode),
                (rs, rowNum) -> new BudgetProjectSnapshot(
                        rs.getString("wikidata_id"),
                        rs.getString("name"),
                        rs.getString("country_code"),
                        rs.getString("country_name"),
                        rs.getString("category"),
                        rs.getString("difficulty"),
                        rs.getString("lifecycle_status"),
                        rs.getObject("opening_year", Integer.class),
                        rs.getBigDecimal("answer_cost_usd"),
                        rs.getString("cost_kind"),
                        rs.getString("metric_type"),
                        rs.getBigDecimal("metric_value"),
                        rs.getString("metric_unit"),
                        rs.getBoolean("eligible"),
                        rs.getString("object_key"),
                        rs.getString("source_sha1"),
                        rs.getString("source_image_url"),
                        rs.getString("source_page_url"),
                        rs.getString("author_name"),
                        rs.getString("attribution_text"),
                        rs.getString("license_label"),
                        rs.getString("license_url")));
    }
}
