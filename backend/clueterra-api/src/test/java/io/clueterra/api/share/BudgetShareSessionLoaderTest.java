package io.clueterra.api.share;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import io.clueterra.api.share.dto.BudgetShareBuilding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.budget.model.BudgetGameAnswerRow;
import io.clueterra.api.budget.model.BudgetGameSessionRow;
import io.clueterra.api.budget.model.BudgetGameSessionStatus;
import io.clueterra.api.budget.repository.BudgetGameSessionJdbcRepository;

@ExtendWith(MockitoExtension.class)
class BudgetShareSessionLoaderTest {

    private static final long SESSION_ID = 42L;
    private static final Instant ANSWERED_AT = Instant.parse("2026-09-25T18:00:00Z");

    @Mock
    private BudgetGameSessionJdbcRepository sessionRepository;

    @InjectMocks
    private BudgetShareSessionLoader loader;

    @Test
    void loadsSpoilerFreeSkylineDataForCompletedSession() {
        when(sessionRepository.findSession(SESSION_ID)).thenReturn(Optional.of(session(
                BudgetGameSessionStatus.COMPLETED)));
        when(sessionRepository.findAnswers(SESSION_ID)).thenReturn(List.of(
                answer(2, "0", 0),
                answer(1, "87.5", 900)));

        var result = loader.load(SESSION_ID).orElseThrow();

        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(result.totalXpEarned()).isEqualTo(900);
        assertThat(result.maximumXp()).isEqualTo(2000);
        assertThat(result.averageAccuracy()).isEqualTo(43.75);
        assertThat(result.buildings()).extracting(BudgetShareBuilding::position)
                .containsExactly(1, 2);
        assertThat(result.buildings()).extracting(BudgetShareBuilding::accuracyPercent)
                .containsExactly(87.5, 0.0);
        assertThat(result.buildings()).extracting(BudgetShareBuilding::styleId)
                .containsExactly(5, 2);
    }

    @Test
    void doesNotExposeIncompleteSession() {
        when(sessionRepository.findSession(SESSION_ID)).thenReturn(Optional.of(session(
                BudgetGameSessionStatus.IN_PROGRESS)));

        assertThat(loader.load(SESSION_ID)).isEmpty();
    }

    private BudgetGameSessionRow session(BudgetGameSessionStatus status) {
        return new BudgetGameSessionRow(
                SESSION_ID,
                7L,
                true,
                9L,
                LocalDate.of(2026, 9, 25),
                status,
                status == BudgetGameSessionStatus.COMPLETED ? 6 : 2,
                900,
                0,
                1000,
                2,
                0,
                1,
                ANSWERED_AT.minusSeconds(60),
                status == BudgetGameSessionStatus.COMPLETED ? ANSWERED_AT : null);
    }

    private BudgetGameAnswerRow answer(int position, String accuracy, int xp) {
        return new BudgetGameAnswerRow(
                position,
                position,
                BigDecimal.TEN,
                null,
                BigDecimal.TEN,
                "USD",
                null,
                new BigDecimal(accuracy),
                BigDecimal.valueOf(xp == 0 ? 0 : 100),
                xp,
                BigDecimal.valueOf(100),
                0,
                BigDecimal.valueOf(xp == 0 ? 0 : 100),
                xp,
                new ObjectMapper().createObjectNode(),
                ANSWERED_AT);
    }
}
