package io.clueterra.budget.generation.challenge.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.budget.generation.challenge.model.BudgetChallengeCandidate;
import io.clueterra.budget.generation.challenge.model.BudgetChallengeGenerationTrigger;
import io.clueterra.budget.generation.challenge.model.BudgetChallengePublication;
import io.clueterra.budget.generation.question.model.BudgetQuestionType;
import io.clueterra.budget.project.BudgetDifficulty;
import io.clueterra.budget.project.MegaprojectCategory;
import io.clueterra.budget.util.SqlLoader;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class BudgetDailyChallengeRepository {

    private static final String EXISTS_BY_DATE =
            SqlLoader.load(BudgetDailyChallengeRepository.class, "existsByDate");
    private static final String FIND_CANDIDATES =
            SqlLoader.load(BudgetDailyChallengeRepository.class, "findCandidates");
    private static final String FIND_USED_PROJECT_IDS =
            SqlLoader.load(BudgetDailyChallengeRepository.class, "findUsedProjectIds");
    private static final String CREATE_CHALLENGE =
            SqlLoader.load(BudgetDailyChallengeRepository.class, "createChallenge");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public boolean existsByDate(LocalDate date) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(EXISTS_BY_DATE, Map.of("date", date), Boolean.class));
    }

    public List<BudgetChallengeCandidate> findCandidates() {
        return jdbc.query(
                FIND_CANDIDATES,
                Map.of(),
                (resultSet, rowNumber) -> new BudgetChallengeCandidate(
                        resultSet.getLong("variant_id"),
                        BudgetQuestionType.valueOf(resultSet.getString("question_type")),
                        BudgetDifficulty.valueOf(resultSet.getString("difficulty")),
                        resultSet.getLong("source_project_id"),
                        resultSet.getObject("comparison_project_id", Long.class),
                        MegaprojectCategory.valueOf(resultSet.getString("category"))));
    }

    public Set<Long> findUsedProjectIds(LocalDate fromDate, LocalDate toDate) {
        return Set.copyOf(jdbc.queryForList(
                FIND_USED_PROJECT_IDS,
                Map.of("fromDate", fromDate, "toDate", toDate),
                Long.class));
    }

    public BudgetChallengePublication create(
            LocalDate date,
            BudgetChallengeGenerationTrigger trigger,
            List<Long> questionVariantIds) {
        String ids = objectMapper.valueToTree(questionVariantIds).toString();
        return jdbc.queryForObject(
                CREATE_CHALLENGE,
                Map.of(
                        "date", date,
                        "trigger", trigger.name(),
                        "questionVariantIds", ids),
                (resultSet, rowNumber) -> new BudgetChallengePublication(
                        resultSet.getLong("challenge_id"),
                        resultSet.getDate("challenge_date").toLocalDate(),
                        resultSet.getBoolean("created")));
    }
}
