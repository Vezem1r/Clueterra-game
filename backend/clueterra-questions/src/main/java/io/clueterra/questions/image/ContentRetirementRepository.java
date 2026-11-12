package io.clueterra.questions.image;

import io.clueterra.questions.repository.util.SqlLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class ContentRetirementRepository {

    private static final String ARCHIVE_DELETED_HINTS_SQL =
            SqlLoader.load(ContentRetirementRepository.class, "archiveDeletedHints");
    private static final String FIND_FUTURE_DELETED_HINT_REFERENCES_SQL =
            SqlLoader.load(ContentRetirementRepository.class, "findFutureDeletedHintReferences");
    private static final String FIND_ARCHIVED_BUDGET_HINT_REFERENCES_SQL =
            SqlLoader.load(ContentRetirementRepository.class,
                    "findCurrentAndFutureArchivedBudgetHintReferences");

    private final NamedParameterJdbcTemplate jdbc;

    public int archiveDeletedHints() {
        Integer retired = jdbc.queryForObject(ARCHIVE_DELETED_HINTS_SQL, Map.of(), Integer.class);
        return retired != null ? retired : 0;
    }

    public List<ChallengeHintReference> findFutureDeletedHintReferences() {
        return jdbc.query(
                FIND_FUTURE_DELETED_HINT_REFERENCES_SQL,
                Map.of(),
                (resultSet, rowNumber) -> new ChallengeHintReference(
                        resultSet.getLong("challenge_id"),
                        resultSet.getInt("country_order"),
                        resultSet.getInt("hint_order")));
    }

    public List<ChallengeHintReference> findCurrentAndFutureArchivedBudgetHintReferences() {
        return jdbc.query(
                FIND_ARCHIVED_BUDGET_HINT_REFERENCES_SQL,
                Map.of(),
                (resultSet, rowNumber) -> new ChallengeHintReference(
                        resultSet.getLong("challenge_id"),
                        resultSet.getInt("country_order"),
                        resultSet.getInt("hint_order")));
    }
}
