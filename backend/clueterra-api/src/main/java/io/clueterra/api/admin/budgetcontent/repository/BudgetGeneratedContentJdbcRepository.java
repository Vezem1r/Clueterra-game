package io.clueterra.api.admin.budgetcontent.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.admin.budgetcontent.dto.BudgetGeneratedChallengeDto;
import io.clueterra.api.admin.budgetcontent.dto.BudgetGeneratedChallengeQuestionDto;
import io.clueterra.api.admin.budgetcontent.dto.BudgetChallengePeriod;
import io.clueterra.api.admin.budgetcontent.dto.BudgetGeneratedQuestionDto;
import io.clueterra.api.common.utils.database.QueryFilterBuilder;
import io.clueterra.api.common.utils.loader.SqlLoader;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class BudgetGeneratedContentJdbcRepository {

    private static final String FIND_QUESTIONS =
            SqlLoader.load(BudgetGeneratedContentJdbcRepository.class, "findQuestions");
    private static final String COUNT_QUESTIONS =
            SqlLoader.load(BudgetGeneratedContentJdbcRepository.class, "countQuestions");
    private static final String FIND_CHALLENGES =
            SqlLoader.load(BudgetGeneratedContentJdbcRepository.class, "findChallenges");
    private static final String COUNT_CHALLENGES =
            SqlLoader.load(BudgetGeneratedContentJdbcRepository.class, "countChallenges");
    private static final String DELETE_UPCOMING_CHALLENGE =
            SqlLoader.load(BudgetGeneratedContentJdbcRepository.class, "deleteUpcomingChallenge");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public List<BudgetGeneratedQuestionDto> findQuestions(
            BudgetGeneratedContentFilter filter, int limit, int offset) {
        QueryFilterBuilder filters = questionFilters(filter);
        filters.params().addValue("limit", limit).addValue("offset", offset);
        return jdbc.query(
                FIND_QUESTIONS + filters.whereClause()
                        + " ORDER BY variant.generated_at DESC, variant.id DESC LIMIT :limit OFFSET :offset",
                filters.params(),
                (resultSet, rowNumber) -> mapQuestion(resultSet));
    }

    public long countQuestions(BudgetGeneratedContentFilter filter) {
        QueryFilterBuilder filters = questionFilters(filter);
        Long count = jdbc.queryForObject(COUNT_QUESTIONS + filters.whereClause(), filters.params(), Long.class);
        return count == null ? 0 : count;
    }

    public List<BudgetGeneratedChallengeDto> findChallenges(
            BudgetChallengePeriod period, int limit, int offset) {
        return jdbc.query(
                FIND_CHALLENGES,
                new MapSqlParameterSource()
                        .addValue("period", period.name())
                        .addValue("limit", limit)
                        .addValue("offset", offset),
                (resultSet, rowNumber) -> mapChallenge(resultSet));
    }

    public long countChallenges(BudgetChallengePeriod period) {
        Long count = jdbc.queryForObject(
                COUNT_CHALLENGES, Map.of("period", period.name()), Long.class);
        return count == null ? 0 : count;
    }

    public Optional<BudgetChallengeDeletionResult> deleteUpcomingChallenge(long challengeId) {
        return jdbc.query(
                        DELETE_UPCOMING_CHALLENGE,
                        Map.of("challengeId", challengeId),
                        (resultSet, rowNumber) -> new BudgetChallengeDeletionResult(
                                resultSet.getLong("challenge_id"),
                                resultSet.getObject("challenge_date", java.time.LocalDate.class),
                                resultSet.getBoolean("future"),
                                resultSet.getBoolean("deleted"),
                                resultSet.getBoolean("has_sessions")))
                .stream()
                .findFirst();
    }

    private QueryFilterBuilder questionFilters(BudgetGeneratedContentFilter filter) {
        QueryFilterBuilder filters = new QueryFilterBuilder()
                .eq("variant.question_type", "questionType", filter.questionType())
                .ilike(
                        "LOWER(source_question.name_snapshot) LIKE :search"
                                + " OR LOWER(COALESCE(comparison_question.name_snapshot, '')) LIKE :search",
                        "search",
                        filter.search());
        if ("ACTIVE".equals(filter.status())) {
            filters.predicate("variant.is_active");
        } else if ("RETIRED".equals(filter.status())) {
            filters.predicate("NOT variant.is_active");
        }
        return filters;
    }

    private BudgetGeneratedQuestionDto mapQuestion(ResultSet resultSet) throws SQLException {
        return new BudgetGeneratedQuestionDto(
                resultSet.getLong("id"),
                resultSet.getString("question_type"),
                resultSet.getString("generation_key"),
                resultSet.getString("difficulty"),
                resultSet.getBoolean("is_active"),
                resultSet.getString("project_name"),
                resultSet.getString("country_name"),
                resultSet.getString("category"),
                resultSet.getString("comparison_project_name"),
                resultSet.getBigDecimal("answer_value"),
                resultSet.getString("answer_unit"),
                resultSet.getString("answer_choice"),
                json(resultSet.getString("prompt_payload")),
                json(resultSet.getString("reveal_payload")),
                instant(resultSet, "generated_at"),
                instant(resultSet, "retired_at"));
    }

    private BudgetGeneratedChallengeDto mapChallenge(ResultSet resultSet) throws SQLException {
        try {
            List<BudgetGeneratedChallengeQuestionDto> questions = objectMapper.readerForListOf(
                            BudgetGeneratedChallengeQuestionDto.class)
                    .readValue(resultSet.getString("questions"));
            return new BudgetGeneratedChallengeDto(
                    resultSet.getLong("id"),
                    resultSet.getObject("challenge_date", java.time.LocalDate.class),
                    resultSet.getString("generation_trigger"),
                    resultSet.getString("status"),
                    instant(resultSet, "published_at"),
                    resultSet.getBoolean("deletable"),
                    questions);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid generated challenge payload", exception);
        }
    }

    private JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid generated question payload", exception);
        }
    }

    private java.time.Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
