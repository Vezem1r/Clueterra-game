package io.clueterra.api.projectpages.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.common.utils.database.QueryFilterBuilder;
import io.clueterra.api.common.utils.loader.SqlLoader;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ProjectPageJdbcRepository {

    private static final String FIND_PUBLISHED_CARDS =
            SqlLoader.load(ProjectPageJdbcRepository.class, "findPublishedCards");
    private static final String COUNT_PUBLISHED_CARDS =
            SqlLoader.load(ProjectPageJdbcRepository.class, "countPublishedCards");
    private static final String FIND_RELATED_CARDS =
            SqlLoader.load(ProjectPageJdbcRepository.class, "findRelatedCards");
    private static final String FIND_DETAIL =
            SqlLoader.load(ProjectPageJdbcRepository.class, "findDetail");
    private static final String FIND_SITEMAP_ROWS =
            SqlLoader.load(ProjectPageJdbcRepository.class, "findSitemapRows");
    private static final String COUNT_HUB_CATEGORIES =
            SqlLoader.load(ProjectPageJdbcRepository.class, "countHubCategories");
    private static final String FIND_HUB_COUNTRIES =
            SqlLoader.load(ProjectPageJdbcRepository.class, "findHubCountries");
    private static final String LIFECYCLE_STATUSES_PARAM = "lifecycleStatuses";
    private static final List<String> RANKED_LIFECYCLE_STATUSES =
            List.of("COMPLETED", "UNDER_CONSTRUCTION", "ON_HOLD", "UNKNOWN");
    private static final String HUB_CARDS_ORDER =
            " ORDER BY cost.answer_cost_usd DESC, project.name ASC LIMIT :limit";
    private static final String PUBLISHED_CARDS_ORDER =
            " ORDER BY seo.published_at DESC, project.name ASC LIMIT :limit OFFSET :offset";
    private static final String DIRECTORY_SEARCH_PREDICATE = """
            LOWER(project.name) LIKE :search
            OR CASE project.category
                WHEN 'METRO_RAIL' THEN 'railway metro rail'
                WHEN 'SPACE' THEN 'space space mission'
                WHEN 'ROAD_HIGHWAY' THEN 'road highway roads highways'
                WHEN 'PORT' THEN 'port ports harbour harbor'
                ELSE REPLACE(LOWER(project.category), '_', ' ')
            END LIKE :search
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public List<ProjectPageCardRow> findPublishedCards(
            ProjectDirectoryFilter filter,
            int limit,
            int offset) {
        QueryFilterBuilder filters = publishedCardFilters(filter);
        filters.params()
                .addValue("limit", limit)
                .addValue("offset", offset);
        return jdbc.query(
                FIND_PUBLISHED_CARDS + filters.whereClause() + PUBLISHED_CARDS_ORDER,
                filters.params(),
                cardRowMapper());
    }

    public long countPublishedCards(ProjectDirectoryFilter filter) {
        QueryFilterBuilder filters = publishedCardFilters(filter);
        Long result = jdbc.queryForObject(
                COUNT_PUBLISHED_CARDS + filters.whereClause(),
                filters.params(),
                Long.class);
        return result == null ? 0 : result;
    }

    private QueryFilterBuilder publishedCardFilters(ProjectDirectoryFilter filter) {
        return new QueryFilterBuilder()
                .eq("seo.publication_status", "publicationStatus", "PUBLISHED")
                .ilike(DIRECTORY_SEARCH_PREDICATE, "search", filter.search())
                .eq("project.category", "category", filter.category())
                .gteDecimal("cost.answer_cost_usd", "minimumCostUsd", filter.minimumCostUsd())
                .lteDecimal("cost.answer_cost_usd", "maximumCostUsd", filter.maximumCostUsd())
                .gteInt("project.project_year", "minimumYear", filter.minimumYear())
                .lteInt("project.project_year", "maximumYear", filter.maximumYear());
    }

    public List<ProjectHubCardRow> findHubCards(String category, String countryCode, int limit) {
        QueryFilterBuilder filters = new QueryFilterBuilder()
                .eq("seo.publication_status", "publicationStatus", "PUBLISHED")
                .eq("project.category", "category", category)
                .eq("project.country_code", "countryCode", countryCode)
                .in("lifecycle.lifecycle_status", LIFECYCLE_STATUSES_PARAM, RANKED_LIFECYCLE_STATUSES);
        filters.params().addValue("limit", limit);
        return jdbc.query(
                FIND_PUBLISHED_CARDS + filters.whereClause() + HUB_CARDS_ORDER,
                filters.params(),
                (resultSet, rowNumber) -> new ProjectHubCardRow(
                        resultSet.getString("wikidata_id"),
                        resultSet.getString("slug"),
                        resultSet.getString("name"),
                        resultSet.getString("country_name"),
                        resultSet.getString("category"),
                        resultSet.getObject("project_year", Integer.class),
                        resultSet.getBigDecimal("answer_cost_usd"),
                        resultSet.getBigDecimal("source_amount"),
                        resultSet.getString("source_currency"),
                        resultSet.getString("cost_kind"),
                        resultSet.getString("scope_label"),
                        resultSet.getString("lifecycle_status"),
                        resultSet.getString("ranking_scope"),
                        resultSet.getString("image_source_sha1"),
                        resultSet.getString("image_alt_text")));
    }

    public List<ProjectHubCategoryRow> countHubCategories() {
        return jdbc.query(
                COUNT_HUB_CATEGORIES,
                Map.of(LIFECYCLE_STATUSES_PARAM, RANKED_LIFECYCLE_STATUSES),
                (resultSet, rowNumber) -> new ProjectHubCategoryRow(
                        resultSet.getString("category"),
                        resultSet.getLong("project_count")));
    }

    public List<ProjectHubCountryRow> findHubCountries(int minimumProjects) {
        return jdbc.query(
                FIND_HUB_COUNTRIES,
                Map.of("minimumProjects", minimumProjects, LIFECYCLE_STATUSES_PARAM, RANKED_LIFECYCLE_STATUSES),
                (resultSet, rowNumber) -> new ProjectHubCountryRow(
                        resultSet.getString("country_code"),
                        resultSet.getString("country_name"),
                        resultSet.getLong("project_count")));
    }

    public List<ProjectPageCardRow> findRelatedCards(String wikidataId, int limit) {
        return jdbc.query(
                FIND_RELATED_CARDS,
                Map.of("wikidataId", wikidataId, "limit", limit),
                cardRowMapper());
    }

    public Optional<ProjectPageDetailRow> findPublishedBySlug(String slug) {
        return findDetail(slug, null, true);
    }

    public Optional<ProjectPageDetailRow> findByWikidataId(String wikidataId) {
        return findDetail(null, wikidataId, false);
    }

    private Optional<ProjectPageDetailRow> findDetail(
            String slug,
            String wikidataId,
            boolean requirePublished) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("slug", slug)
                .addValue("wikidataId", wikidataId)
                .addValue("requirePublished", requirePublished);
        return jdbc.query(
                        FIND_DETAIL,
                        parameters,
                        detailRowMapper())
                .stream()
                .findFirst();
    }

    public List<ProjectSitemapRow> findSitemapRows() {
        return jdbc.query(
                FIND_SITEMAP_ROWS,
                Map.of(),
                (resultSet, rowNumber) -> new ProjectSitemapRow(
                        resultSet.getString("slug"),
                        resultSet.getTimestamp("updated_at").toInstant()));
    }

    private RowMapper<ProjectPageCardRow> cardRowMapper() {
        return (resultSet, rowNumber) -> new ProjectPageCardRow(
                resultSet.getString("wikidata_id"),
                resultSet.getString("slug"),
                resultSet.getString("name"),
                resultSet.getString("country_name"),
                resultSet.getString("category"),
                resultSet.getObject("project_year", Integer.class),
                resultSet.getBigDecimal("answer_cost_usd"),
                resultSet.getBigDecimal("source_amount"),
                resultSet.getString("source_currency"),
                resultSet.getString("meta_description"),
                resultSet.getString("image_source_sha1"),
                resultSet.getString("image_alt_text"),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    private RowMapper<ProjectPageDetailRow> detailRowMapper() {
        return (resultSet, rowNumber) -> new ProjectPageDetailRow(
                resultSet.getString("wikidata_id"),
                resultSet.getString("slug"),
                resultSet.getString("cost_question"),
                resultSet.getString("seo_title"),
                resultSet.getString("meta_description"),
                resultSet.getString("keywords"),
                resultSet.getString("intro"),
                resultSet.getString("answer_detail"),
                resultSet.getString("scope_text"),
                resultSet.getString("context_text"),
                resultSet.getString("methodology_text"),
                resultSet.getString("source_name"),
                resultSet.getString("source_url"),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getString("name"),
                resultSet.getString("country_code"),
                resultSet.getString("country_name"),
                resultSet.getString("category"),
                resultSet.getObject("project_year", Integer.class),
                resultSet.getBigDecimal("answer_cost_usd"),
                resultSet.getBigDecimal("source_amount"),
                resultSet.getString("source_currency"),
                resultSet.getObject("cost_year", Integer.class),
                resultSet.getString("cost_kind"),
                resultSet.getString("scope_label"),
                resultSet.getString("lifecycle_status"),
                resultSet.getString("image_source_sha1"),
                resultSet.getString("image_alt_text"),
                resultSet.getString("image_author_name"),
                resultSet.getString("image_attribution_text"),
                resultSet.getString("image_license_label"),
                resultSet.getString("image_license_url"),
                resultSet.getString("image_source_page_url"),
                readJsonList(resultSet, "costs_json", ProjectPageCostRow.class),
                readJsonList(resultSet, "metrics_json", ProjectPageMetricRow.class),
                readJsonList(resultSet, "milestones_json", ProjectPageMilestoneRow.class),
                readJsonList(resultSet, "facts_json", ProjectPageFactRow.class),
                readJsonList(resultSet, "sources_json", ProjectPageSourceRow.class));
    }

    private <T> List<T> readJsonList(ResultSet resultSet, String column, Class<T> elementType)
            throws SQLException {
        String json = resultSet.getString(column);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
        try {
            return List.copyOf(objectMapper.readValue(json, listType));
        } catch (JsonProcessingException exception) {
            throw new SQLException("Could not parse project page column " + column, exception);
        }
    }
}
