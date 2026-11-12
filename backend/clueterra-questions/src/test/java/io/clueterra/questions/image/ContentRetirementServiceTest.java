package io.clueterra.questions.image;

import io.clueterra.questions.service.challenge.ChallengeRerollService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentRetirementServiceTest {

    @Mock
    private ContentRetirementRepository repository;

    @Mock
    private ChallengeRerollService challengeRerollService;

    @InjectMocks
    private ContentRetirementService service;

    @Test
    void rerollsFutureReferencesBeforeArchivingAndRetriesFailuresLater() {
        ChallengeHintReference replaced = new ChallengeHintReference(11L, 1, 2);
        ChallengeHintReference retryLater = new ChallengeHintReference(12L, 3, 4);
        when(repository.findFutureDeletedHintReferences()).thenReturn(List.of(replaced, retryLater));
        when(challengeRerollService.rerollHint(11L, 1, 2)).thenReturn(null);
        when(challengeRerollService.rerollHint(12L, 3, 4))
                .thenThrow(new IllegalStateException("No replacement"));
        when(repository.archiveDeletedHints()).thenReturn(1);

        int retired = service.retireSuppressedContent();

        assertThat(retired).isEqualTo(1);
        InOrder order = inOrder(challengeRerollService, repository);
        order.verify(challengeRerollService).rerollHint(11L, 1, 2);
        order.verify(challengeRerollService).rerollHint(12L, 3, 4);
        order.verify(repository).archiveDeletedHints();
    }

    @Test
    void rerollsArchivedBudgetHintsInExistingChallenges() {
        when(repository.findCurrentAndFutureArchivedBudgetHintReferences())
                .thenReturn(List.of(new ChallengeHintReference(23L, 2, 1)));

        service.rerollArchivedBudgetHints();

        verify(challengeRerollService).rerollHint(23L, 2, 1);
    }
}
