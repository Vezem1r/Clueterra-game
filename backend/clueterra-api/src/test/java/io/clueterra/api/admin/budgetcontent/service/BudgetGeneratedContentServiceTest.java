package io.clueterra.api.admin.budgetcontent.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.clueterra.api.admin.budgetcontent.repository.BudgetChallengeDeletionResult;
import io.clueterra.api.admin.budgetcontent.repository.BudgetGeneratedContentJdbcRepository;
import io.clueterra.api.exception.ResourceConflictException;
import io.clueterra.api.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class BudgetGeneratedContentServiceTest {

    @Mock
    private BudgetGeneratedContentJdbcRepository repository;

    private BudgetGeneratedContentService service;

    @BeforeEach
    void setUp() {
        service = new BudgetGeneratedContentService(repository);
    }

    @Test
    void deletesFutureChallenge() {
        when(repository.deleteUpcomingChallenge(42L)).thenReturn(Optional.of(
                new BudgetChallengeDeletionResult(
                        42L, LocalDate.of(2026, 10, 1), true, true, false)));

        assertThatCode(() -> service.deleteUpcomingChallenge(42L)).doesNotThrowAnyException();
    }

    @Test
    void protectsTodayAndPastChallenges() {
        when(repository.deleteUpcomingChallenge(42L)).thenReturn(Optional.of(
                new BudgetChallengeDeletionResult(
                        42L, LocalDate.of(2026, 9, 27), false, false, false)));

        assertThatThrownBy(() -> service.deleteUpcomingChallenge(42L))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("Today's and past");
    }

    @Test
    void protectsFutureChallengeWithSessions() {
        when(repository.deleteUpcomingChallenge(42L)).thenReturn(Optional.of(
                new BudgetChallengeDeletionResult(
                        42L, LocalDate.of(2026, 10, 1), true, false, true)));

        assertThatThrownBy(() -> service.deleteUpcomingChallenge(42L))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("player sessions");
    }

    @Test
    void reportsMissingChallenge() {
        when(repository.deleteUpcomingChallenge(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteUpcomingChallenge(42L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }
}
