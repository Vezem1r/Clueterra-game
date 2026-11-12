package io.clueterra.budget.generation.question.repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.budget.generation.question.model.BudgetGeneratedQuestionDraft;
import io.clueterra.budget.generation.question.model.BudgetQuestionCostFact;
import io.clueterra.budget.generation.question.model.BudgetQuestionMetricFact;
import io.clueterra.budget.generation.question.model.BudgetQuestionMilestoneFact;
import io.clueterra.budget.generation.question.model.BudgetQuestionVariantReplacement;
import io.clueterra.budget.generation.question.model.BudgetSourceQuestion;
import io.clueterra.budget.project.BudgetDifficulty;
import io.clueterra.budget.project.MegaprojectCategory;
import io.clueterra.budget.project.ProjectCostKind;
import io.clueterra.budget.project.ProjectDatePrecision;
import io.clueterra.budget.project.ProjectMetricType;
import io.clueterra.budget.project.ProjectMilestoneType;
import io.clueterra.budget.project.ProjectLifecycleStatus;
import io.clueterra.budget.util.SqlLoader;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class BudgetQuestionGenerationRepository {

    private static final String FIND_SOURCE_QUESTIONS =
            SqlLoader.load(BudgetQuestionGenerationRepository.class, "findSourceQuestions");
    private static final String FIND_COST_FACTS =
            SqlLoader.load(BudgetQuestionGenerationRepository.class, "findCostFacts");
    private static final String FIND_METRIC_FACTS =
            SqlLoader.load(BudgetQuestionGenerationRepository.class, "findMetricFacts");
    private static final String FIND_MILESTONE_FACTS =
            SqlLoader.load(BudgetQuestionGenerationRepository.class, "findMilestoneFacts");
    private static final String REPLACE_VARIANTS =
            SqlLoader.load(BudgetQuestionGenerationRepository.class, "replaceVariants");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public List<BudgetSourceQuestion> findSourceQuestions() {
        return jdbc.query(
                FIND_SOURCE_QUESTIONS,
                Map.of(),
                (resultSet, rowNumber) ->
                        new BudgetSourceQuestion(
                                resultSet.getLong("question_id"),
                                resultSet.getLong("project_id"),
                                resultSet.getString("wikidata_id"),
                                resultSet.getString("name_snapshot"),
                                resultSet.getString("description_snapshot"),
                                resultSet.getString("description_source_name"),
                                resultSet.getString("description_source_url"),
                                resultSet.getString("description_license_label"),
                                resultSet.getString("description_license_url"),
                                resultSet.getString("country_code"),
                                resultSet.getString("country_name"),
                                resultSet.getString("region_name"),
                                MegaprojectCategory.valueOf(resultSet.getString("category")),
                                resultSet.getObject("project_year", Integer.class),
                                ProjectCostKind.valueOf(resultSet.getString("answer_cost_kind")),
                                ProjectLifecycleStatus.valueOf(resultSet.getString("lifecycle_status")),
                                resultSet.getObject("expected_completion_year", Integer.class),
                                resultSet.getBigDecimal("answer_cost_usd"),
                                resultSet.getString("cost_source_name"),
                                resultSet.getString("cost_source_url"),
                                resultSet.getString("cost_reference_url"),
                                BudgetDifficulty.valueOf(resultSet.getString("difficulty"))));
    }

    public List<BudgetQuestionCostFact> findCostFacts() {
        return jdbc.query(
                FIND_COST_FACTS,
                Map.of(),
                (resultSet, rowNumber) ->
                        new BudgetQuestionCostFact(
                                resultSet.getLong("project_id"),
                                ProjectCostKind.valueOf(resultSet.getString("cost_kind")),
                                resultSet.getBigDecimal("source_amount_low"),
                                resultSet.getBigDecimal("source_amount_high"),
                                resultSet.getString("source_currency"),
                                resultSet.getBigDecimal("amount_usd_low"),
                                resultSet.getBigDecimal("amount_usd_high"),
                                resultSet.getObject("cost_year", Integer.class),
                                resultSet.getBigDecimal("units_per_usd"),
                                localDate(resultSet.getDate("exchange_rate_date")),
                                resultSet.getString("exchange_rate_basis"),
                                resultSet.getString("exchange_rate_provider"),
                                resultSet.getString("evidence_text"),
                                resultSet.getString("scope_note"),
                                resultSet.getString("source_name"),
                                resultSet.getString("source_url"),
                                resultSet.getString("reference_url")));
    }

    public List<BudgetQuestionMetricFact> findMetricFacts() {
        return jdbc.query(
                FIND_METRIC_FACTS,
                Map.of(),
                (resultSet, rowNumber) ->
                        new BudgetQuestionMetricFact(
                                resultSet.getLong("project_id"),
                                ProjectMetricType.valueOf(resultSet.getString("metric_type")),
                                resultSet.getBigDecimal("normalized_value"),
                                resultSet.getString("normalized_unit"),
                                resultSet.getString("source_name"),
                                resultSet.getString("source_url"),
                                resultSet.getString("reference_url")));
    }

    public List<BudgetQuestionMilestoneFact> findMilestoneFacts() {
        return jdbc.query(
                FIND_MILESTONE_FACTS,
                Map.of(),
                (resultSet, rowNumber) ->
                        new BudgetQuestionMilestoneFact(
                                resultSet.getLong("project_id"),
                                ProjectMilestoneType.valueOf(resultSet.getString("milestone_type")),
                                localDate(resultSet.getDate("milestone_date")),
                                resultSet.getObject("milestone_year", Integer.class),
                                ProjectDatePrecision.valueOf(resultSet.getString("date_precision")),
                                resultSet.getString("source_name"),
                                resultSet.getString("source_url"),
                                resultSet.getString("reference_url")));
    }

    public BudgetQuestionVariantReplacement replaceVariants(
            List<BudgetGeneratedQuestionDraft> variants) {
        String payload = objectMapper.valueToTree(variants).toString();
        return jdbc.queryForObject(
                REPLACE_VARIANTS,
                Map.of("variants", payload),
                (resultSet, rowNumber) ->
                        new BudgetQuestionVariantReplacement(
                                resultSet.getInt("generated_count"),
                                resultSet.getInt("created_count"),
                                resultSet.getInt("retired_count")));
    }

    private LocalDate localDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }
}
