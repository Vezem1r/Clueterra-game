package io.clueterra.api.admin.budgetcontent.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoPageDto;
import io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoPageRequest;
import io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoFactInput;
import io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoReferenceInput;
import io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoCandidateDto;
import io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoStatus;
import io.clueterra.api.common.utils.loader.SqlLoader;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class BudgetProjectSeoJdbcRepository {

    private static final String FIND_PAGE =
            SqlLoader.load(BudgetProjectSeoJdbcRepository.class, "findPage");
    private static final String COUNT_PAGE =
            SqlLoader.load(BudgetProjectSeoJdbcRepository.class, "countPage");
    private static final String UPSERT =
            SqlLoader.load(BudgetProjectSeoJdbcRepository.class, "upsert");
    private static final String FIND_CANDIDATES =
            SqlLoader.load(BudgetProjectSeoJdbcRepository.class, "findCandidates");
    private static final String COUNT_CANDIDATES =
            SqlLoader.load(BudgetProjectSeoJdbcRepository.class, "countCandidates");
    private static final String FIND_EDITORIAL_CONTEXT =
            SqlLoader.load(BudgetProjectSeoJdbcRepository.class, "findEditorialContext");
    private static final String SLUG_OWNED_BY_ANOTHER =
            SqlLoader.load(BudgetProjectSeoJdbcRepository.class, "slugOwnedByAnother");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public List<BudgetProjectSeoPageDto> findPage(
            String search,
            String publicationStatus,
            int limit,
            int offset) {
        return jdbc.query(
                FIND_PAGE,
                pageParameters(search, publicationStatus)
                        .addValue("limit", limit)
                        .addValue("offset", offset),
                rowMapper());
    }

    public long count(String search, String publicationStatus) {
        Long result = jdbc.queryForObject(
                COUNT_PAGE,
                pageParameters(search, publicationStatus),
                Long.class);
        return result == null ? 0 : result;
    }

    public List<BudgetProjectSeoCandidateDto> findCandidates(
            String search,
            String pageStatus,
            int limit,
            int offset) {
        return jdbc.query(
                FIND_CANDIDATES,
                candidateParameters(search, pageStatus)
                        .addValue("limit", limit)
                        .addValue("offset", offset),
                (resultSet, rowNumber) -> new BudgetProjectSeoCandidateDto(
                        resultSet.getString("wikidata_id"),
                        resultSet.getString("project_name"),
                        resultSet.getString("country_name"),
                        resultSet.getString("category"),
                        resultSet.getObject("project_year", Integer.class),
                        resultSet.getBigDecimal("answer_cost_usd"),
                        resultSet.getString("source_currency"),
                        resultSet.getString("content_quality_status"),
                        resultSet.getString("cost_evidence_tier"),
                        resultSet.getInt("cost_confidence_score"),
                        resultSet.getBoolean("image_available"),
                        resultSet.getBoolean("primary_cost_available"),
                        resultSet.getInt("fact_count"),
                        nullableStatus(resultSet.getString("publication_status")),
                        resultSet.getString("slug")));
    }

    public long countCandidates(String search, String pageStatus) {
        Long result = jdbc.queryForObject(
                COUNT_CANDIDATES,
                candidateParameters(search, pageStatus),
                Long.class);
        return result == null ? 0 : result;
    }

    public Optional<BudgetProjectSeoEditorialContext> findEditorialContext(String wikidataId) {
        return jdbc.query(
                        FIND_EDITORIAL_CONTEXT,
                        new MapSqlParameterSource("wikidataId", wikidataId),
                        (resultSet, rowNumber) -> new BudgetProjectSeoEditorialContext(
                                resultSet.getString("wikidata_id"),
                                resultSet.getString("project_name"),
                                resultSet.getString("description"),
                                resultSet.getString("country_name"),
                                resultSet.getString("category"),
                                resultSet.getObject("project_year", Integer.class),
                                resultSet.getBigDecimal("answer_cost_usd"),
                                resultSet.getBigDecimal("source_amount"),
                                resultSet.getString("source_currency"),
                                resultSet.getObject("cost_year", Integer.class),
                                resultSet.getString("normalization_note"),
                                resultSet.getString("cost_source_name"),
                                resultSet.getString("cost_source_url"),
                                resultSet.getString("cost_evidence_tier"),
                                resultSet.getInt("cost_confidence_score"),
                                resultSet.getBoolean("has_quality_flags"),
                                resultSet.getBoolean("image_available"),
                                resultSet.getBoolean("image_metadata_complete"),
                                resultSet.getBoolean("primary_cost_available"),
                                resultSet.getString("cost_verification_status"),
                                resultSet.getString("cost_evidence_text"),
                                resultSet.getString("cost_scope_note"),
                                resultSet.getInt("metric_count"),
                                resultSet.getInt("milestone_count")))
                .stream()
                .findFirst();
    }

    public boolean slugOwnedByAnother(String wikidataId, String slug) {
        if (slug == null || slug.isBlank()) {
            return false;
        }
        Boolean result = jdbc.queryForObject(
                SLUG_OWNED_BY_ANOTHER,
                new MapSqlParameterSource()
                        .addValue("wikidataId", wikidataId)
                        .addValue("slug", slug.trim()),
                Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    public Optional<BudgetProjectSeoPageDto> upsert(
            String wikidataId,
            BudgetProjectSeoPageRequest request,
            String updatedBy) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("wikidataId", wikidataId)
                .addValue("slug", request.slug())
                .addValue("costQuestion", request.costQuestion())
                .addValue("seoTitle", request.seoTitle())
                .addValue("metaDescription", request.metaDescription())
                .addValue("keywords", request.keywords())
                .addValue("intro", request.intro())
                .addValue("answerDetail", request.answerDetail())
                .addValue("scopeText", request.scopeText())
                .addValue("contextText", request.contextText())
                .addValue("methodologyText", request.methodologyText())
                .addValue("sourceName", request.sourceName())
                .addValue("sourceUrl", request.sourceUrl())
                .addValue("referencesJson", objectMapper.valueToTree(request.references()).toString())
                .addValue("factsJson", objectMapper.valueToTree(request.facts()).toString())
                .addValue("publicationStatus", request.publicationStatus().name())
                .addValue("updatedBy", updatedBy);
        return jdbc.query(UPSERT, parameters, rowMapper()).stream().findFirst();
    }

    private RowMapper<BudgetProjectSeoPageDto> rowMapper() {
        return (resultSet, rowNumber) -> map(resultSet);
    }

    private MapSqlParameterSource pageParameters(String search, String publicationStatus) {
        return new MapSqlParameterSource()
                .addValue("search", search)
                .addValue("searchPattern", "%" + search.toLowerCase(Locale.ROOT) + "%")
                .addValue("publicationStatus", publicationStatus);
    }

    private MapSqlParameterSource candidateParameters(String search, String pageStatus) {
        return new MapSqlParameterSource()
                .addValue("search", search)
                .addValue("searchPattern", "%" + search.toLowerCase(Locale.ROOT) + "%")
                .addValue("pageStatus", pageStatus);
    }

    private BudgetProjectSeoStatus nullableStatus(String value) {
        return value == null ? null : BudgetProjectSeoStatus.valueOf(value);
    }

    private BudgetProjectSeoPageDto map(ResultSet resultSet) throws SQLException {
        return new BudgetProjectSeoPageDto(
                resultSet.getString("wikidata_id"),
                resultSet.getString("project_name"),
                resultSet.getString("country_name"),
                resultSet.getString("category"),
                resultSet.getBoolean("project_available"),
                resultSet.getBoolean("image_available"),
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
                readList(resultSet, "references_json", BudgetProjectSeoReferenceInput.class),
                readList(resultSet, "facts_json", BudgetProjectSeoFactInput.class),
                BudgetProjectSeoStatus.valueOf(resultSet.getString("publication_status")),
                instant(resultSet, "published_at"),
                resultSet.getString("updated_by"),
                instant(resultSet, "updated_at"));
    }

    private <T> List<T> readList(ResultSet resultSet, String column, Class<T> itemType) throws SQLException {
        try {
            return objectMapper.readValue(
                    resultSet.getString(column),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, itemType));
        } catch (JsonProcessingException exception) {
            throw new SQLException("Invalid project SEO JSON in column " + column, exception);
        }
    }

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
