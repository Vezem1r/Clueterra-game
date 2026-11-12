package io.clueterra.api.game.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.clueterra.api.common.enums.SessionStatus;
import io.clueterra.api.exception.GameException;
import io.clueterra.api.game.dto.CountryDto;
import io.clueterra.api.game.dto.GuessResultDto;
import io.clueterra.api.game.dto.HintDto;
import io.clueterra.api.game.dto.RevealResultDto;
import io.clueterra.api.game.repository.CountrySearchJdbcRepository;
import io.clueterra.api.game.repository.GameSessionJdbcRepository;
import io.clueterra.api.game.repository.SessionCountryRow;
import io.clueterra.api.game.repository.SessionRow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameplayService {

    private final GameSessionJdbcRepository sessionRepository;
    private final CountrySearchJdbcRepository countrySearchRepository;
    private final GameSessionService gameSessionService;
    private final GameCompletionService completionService;
    private final ChallengeHintParser hintParser;
    private final SessionActivityService activityService;

    private static final int MIN_SEARCH_LENGTH = 2;

    @Transactional
    public GuessResultDto guess(Long sessionId, String countryCode, Long userId) {
        SessionRow session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> GameException.sessionNotFound(sessionId));

        gameSessionService.validateAccess(session, userId);

        if (session.status() != SessionStatus.IN_PROGRESS) {
            throw GameException.sessionNotInProgress();
        }

        int maxAttempts = session.maxAttempts();

        SessionCountryRow currentCountry = session.countries().get(session.currentCountryIdx());
        boolean canGuess = !currentCountry.isGuessed() && currentCountry.attemptsUsed() < maxAttempts;
        if (!canGuess) {
            throw GameException.noMoreAttempts();
        }

        CountryDto guessedCountry = countrySearchRepository.findByCode(countryCode)
                .orElseThrow(() -> GameException.invalidCountry(countryCode));

        boolean isCorrect = guessedCountry.id().equals(currentCountry.countryId());
        int newAttemptsUsed = currentCountry.attemptsUsed() + 1;
        sessionRepository.insertGuessAttempt(currentCountry.id(), guessedCountry.id(), isCorrect, newAttemptsUsed, currentCountry.hintsRevealed());
        SessionActivityService.ActivitySnapshot activity = activityService.capture(session, currentCountry);

        int xpEarned = 0;
        HintDto nextHint = null;
        boolean countryComplete = false;
        boolean sessionComplete = false;
        int newHintsRevealed = currentCountry.hintsRevealed();

        if (isCorrect) {
            int maxHints = currentCountry.totalHints() > 0 ? currentCountry.totalHints() : session.maxHintsPerCountry();
            xpEarned = completionService.calculateXp(
                    session.challengeLevel(),
                    currentCountry.hintsRevealed(),
                    newAttemptsUsed,
                    activity.updatedCountryTimeMs(),
                    maxHints,
                    session.maxAttempts());
            sessionRepository.updateSessionCountry(
                    currentCountry.id(),
                    currentCountry.hintsRevealed(),
                    newAttemptsUsed,
                    true,
                    activity.occurredAt(),
                    activity.updatedCountryTimeMs(),
                    xpEarned);
            countryComplete = true;
            sessionRepository.updateSessionActivity(sessionId, activity.occurredAt());
            sessionComplete = advanceOrCompleteSession(sessionId, session);
        } else {
            int maxHints = currentCountry.totalHints();
            boolean canRevealHint = currentCountry.hintsRevealed() < maxHints;
            if (canRevealHint) {
                newHintsRevealed = currentCountry.hintsRevealed() + 1;
                nextHint = hintParser.getHintAtPosition(session.hintsJson(), currentCountry.countryOrder(), newHintsRevealed);
            }

            boolean canStillGuess = newAttemptsUsed < maxAttempts;
            sessionRepository.updateSessionCountry(
                    currentCountry.id(),
                    newHintsRevealed,
                    newAttemptsUsed,
                    false,
                    null,
                    activity.updatedCountryTimeMs(),
                    0);
            sessionRepository.updateSessionActivity(sessionId, activity.occurredAt());

            if (!canStillGuess) {
                countryComplete = true;
                sessionComplete = advanceOrCompleteSession(sessionId, session);
            }
        }

        CountryDto correctCountryDto = null;
        if (countryComplete) {
            correctCountryDto = new CountryDto(
                    currentCountry.countryId(), currentCountry.isoAlpha2(),
                    currentCountry.nameEn(), currentCountry.flagEmoji(),
                    currentCountry.flagUrl(), currentCountry.region());
        }

        return new GuessResultDto(isCorrect, maxAttempts - newAttemptsUsed, newHintsRevealed, nextHint, correctCountryDto, xpEarned, countryComplete, sessionComplete);
    }

    @Transactional
    public RevealResultDto revealHint(Long sessionId, Long userId) {
        SessionRow session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> GameException.sessionNotFound(sessionId));

        gameSessionService.validateAccess(session, userId);

        if (session.status() != SessionStatus.IN_PROGRESS) {
            throw GameException.sessionNotInProgress();
        }

        int maxAttempts = session.maxAttempts();

        SessionCountryRow currentCountry = session.countries().get(session.currentCountryIdx());
        int maxHints = currentCountry.totalHints();
        boolean canReveal = !currentCountry.isGuessed() && currentCountry.hintsRevealed() < maxHints;
        if (!canReveal) {
            throw GameException.noMoreHints();
        }

        int newHintsRevealed = currentCountry.hintsRevealed() + 1;
        SessionActivityService.ActivitySnapshot activity = activityService.capture(session, currentCountry);

        sessionRepository.updateSessionCountry(
                currentCountry.id(),
                newHintsRevealed,
                currentCountry.attemptsUsed(),
                false,
                null,
                activity.updatedCountryTimeMs(),
                currentCountry.xpEarned());
        sessionRepository.updateSessionActivity(sessionId, activity.occurredAt());
        HintDto hint = hintParser.getHintAtPosition(session.hintsJson(), currentCountry.countryOrder(), newHintsRevealed);

        return new RevealResultDto(hint, newHintsRevealed, maxAttempts - currentCountry.attemptsUsed());
    }

    public List<CountryDto> searchCountries(String query) {
        if (query == null || query.length() < MIN_SEARCH_LENGTH) {
            return List.of();
        }
        return countrySearchRepository.search(query);
    }

    private boolean advanceOrCompleteSession(long sessionId, SessionRow session) {
        if (session.currentCountryIdx() >= session.countries().size() - 1) {
            log.info("Game session completed: sessionId={}, userId={}", sessionId, session.userId());
            SessionRow refreshed = sessionRepository.findById(sessionId).orElseThrow(() -> GameException.sessionNotFound(sessionId));
            completionService.completeSession(refreshed);
            return true;
        }
        int nextIdx = session.currentCountryIdx() + 1;
        sessionRepository.updateCurrentCountryIdx(sessionId, nextIdx);
        sessionRepository.activateSessionCountry(sessionId, nextIdx + 1);
        return false;
    }
}
