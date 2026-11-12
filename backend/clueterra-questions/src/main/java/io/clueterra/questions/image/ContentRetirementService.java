package io.clueterra.questions.image;

import io.clueterra.questions.service.challenge.ChallengeRerollService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentRetirementService {

    private final ContentRetirementRepository repository;
    private final ChallengeRerollService challengeRerollService;

    public int retireSuppressedContent() {
        repository.findFutureDeletedHintReferences().forEach(this::rerollFutureReference);
        int retired = repository.archiveDeletedHints();
        log.info("[CONTENT_RETIREMENT] Archived {} administratively deleted hints", retired);
        return retired;
    }

    public void rerollArchivedBudgetHints() {
        repository.findCurrentAndFutureArchivedBudgetHintReferences()
                .forEach(this::rerollFutureReference);
    }

    private void rerollFutureReference(ChallengeHintReference reference) {
        try {
            challengeRerollService.rerollHint(
                    reference.challengeId(),
                    reference.countryOrder(),
                    reference.hintOrder());
        } catch (RuntimeException exception) {
            log.warn(
                    "[CONTENT_RETIREMENT] Could not replace retired hint in challenge {} at {}/{}: {}",
                    reference.challengeId(),
                    reference.countryOrder(),
                    reference.hintOrder(),
                    exception.getMessage());
        }
    }
}
