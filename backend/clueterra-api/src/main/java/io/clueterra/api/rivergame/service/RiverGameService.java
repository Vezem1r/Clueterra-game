package io.clueterra.api.rivergame.service;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import io.clueterra.api.achievements.engine.TemplateBadgeEngine;
import io.clueterra.api.auth.repository.AuthJdbcRepository;
import io.clueterra.api.auth.repository.UserRow;
import io.clueterra.api.exception.LegalAcceptanceRequiredException;
import io.clueterra.api.legal.service.LegalAcceptanceService;
import io.clueterra.api.rivergame.dto.RiverAnswerDto;
import io.clueterra.api.rivergame.dto.RiverGuessResultDto;
import io.clueterra.api.rivergame.dto.RiverPhotoDto;
import io.clueterra.api.rivergame.dto.RiverPlayerStatsDto;
import io.clueterra.api.rivergame.dto.RiverQuestionDto;
import io.clueterra.api.rivergame.dto.RiverRevealDto;
import io.clueterra.api.rivergame.dto.RiverSessionDto;
import io.clueterra.api.rivergame.model.RiverGuessRow;
import io.clueterra.api.rivergame.model.RiverGuestStartResult;
import io.clueterra.api.rivergame.model.RiverQuestionRow;
import io.clueterra.api.rivergame.model.RiverRevealRow;
import io.clueterra.api.rivergame.repository.RiverGameJdbcRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RiverGameService {

    private static final String MAP_URL_PREFIX = "/assets/rivers/";
    private static final String MAP_KEY_PREFIX = "river-game/";
    private static final String PHOTO_URL_PREFIX = "/assets/rivers/photos/";
    private static final String PHOTO_KEY_PREFIX = "river-photos/";
    private static final int FIRST_ATTEMPT = 1;
    private static final RiverPlayerStatsDto NEW_PLAYER_STATS = new RiverPlayerStatsDto(0, 0, 0, 0, 0, null);

    private final RiverGameJdbcRepository repository;
    private final AuthJdbcRepository authRepository;
    private final LegalAcceptanceService legalAcceptanceService;
    private final TemplateBadgeEngine badgeEngine;

    @Transactional
    public RiverGuestStartResult startGuest(String guestToken, String termsVersion, String privacyVersion) {
        legalAcceptanceService.validateSubmittedVersions(termsVersion, privacyVersion);
        UserRow guest = Optional.ofNullable(guestToken)
                .filter(token -> !token.isBlank())
                .flatMap(authRepository::findByGuestToken)
                .orElseGet(() -> {
                    long userId = authRepository.insertGuestUser(termsVersion, privacyVersion);
                    return authRepository.findById(userId).orElseThrow();
                });
        authRepository.refreshGuestTokenExpiry(guest.id());
        if (!legalAcceptanceService.isCurrent(
                guest.termsVersionAccepted(), guest.privacyVersionAccepted())) {
            legalAcceptanceService.recordUserAcceptance(
                    guest.id(), termsVersion, privacyVersion,
                    LegalAcceptanceService.SOURCE_GUEST_REACCEPT);
        }
        return new RiverGuestStartResult(guest.guestToken(), selectQuestion(guest.id()));
    }

    @Transactional
    public RiverQuestionDto startRegistered(long userId) {
        ensureCurrentLegalAcceptance(userId);
        return selectQuestion(userId);
    }

    @Transactional
    public RiverQuestionDto next(long userId) {
        ensureCurrentLegalAcceptance(userId);
        return selectQuestion(userId);
    }

    @Transactional(readOnly = true)
    public RiverSessionDto session(Long userId) {
        return Optional.ofNullable(userId)
                .map(id -> new RiverSessionDto(repository.findPlayerStats(id),
                        repository.findCurrent(id).map(this::toDto).orElse(null)))
                .orElseGet(() -> new RiverSessionDto(NEW_PLAYER_STATS, null));
    }

    @Transactional
    public RiverQuestionDto revealHint(long userId, long questionId) {
        ensureCurrentLegalAcceptance(userId);
        repository.revealHint(userId, questionId);
        return currentQuestion(userId);
    }

    @Transactional
    public RiverGuessResultDto guess(long userId, long questionId, String answer) {
        ensureCurrentLegalAcceptance(userId);
        RiverGuessRow result = repository.guess(userId, questionId, answer);
        if (result.correct() || result.attempts() == FIRST_ATTEMPT) {
            badgeEngine.evaluateForUser(userId, null);
        }
        return new RiverGuessResultDto(result.correct(), result.attempts(), result.xpAwarded(),
                result.correct() ? reveal(questionId) : null, repository.findPlayerStats(userId));
    }

    @Transactional
    public RiverAnswerDto showAnswer(long userId, long questionId) {
        ensureCurrentLegalAcceptance(userId);
        repository.showAnswer(userId, questionId);
        return new RiverAnswerDto(reveal(questionId), repository.findPlayerStats(userId));
    }

    @Transactional
    public RiverQuestionDto skip(long userId, long questionId) {
        ensureCurrentLegalAcceptance(userId);
        Long nextQuestionId = repository.skip(userId, questionId);
        if (nextQuestionId == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No river questions are available");
        }
        return currentQuestion(userId);
    }

    @Transactional(readOnly = true)
    public List<String> searchNames(String query) {
        if (query == null || query.strip().length() < 2) {
            return List.of();
        }
        return repository.searchNames(query.strip().substring(0, Math.min(60, query.strip().length())));
    }

    private RiverQuestionDto selectQuestion(long userId) {
        Long questionId = repository.selectNext(userId);
        if (questionId == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No river questions are available");
        }
        return currentQuestion(userId);
    }

    private RiverQuestionDto currentQuestion(long userId) {
        return repository.findCurrent(userId)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "River question changed; resume the game"));
    }

    private RiverRevealDto reveal(long questionId) {
        RiverRevealRow row = repository.findReveal(questionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "River question is unavailable"));
        RiverPhotoDto photo = Optional.ofNullable(row.photoObjectKey())
                .map(key -> new RiverPhotoDto(PHOTO_URL_PREFIX + key.substring(PHOTO_KEY_PREFIX.length()),
                        row.photoAuthorName(), row.photoLicenseLabel(), row.photoLicenseUrl(),
                        row.photoSourcePageUrl()))
                .orElse(null);
        return new RiverRevealDto(row.name(), row.countryCode(), row.lengthKm(), mapUrl(row.bordersImageKey()),
                row.facts(), photo, row.wikipediaUrl(), row.factsSourceUrl());
    }

    private String mapUrl(String imageKey) {
        return MAP_URL_PREFIX + imageKey.substring(MAP_KEY_PREFIX.length());
    }

    private RiverQuestionDto toDto(RiverQuestionRow row) {
        String imageKey = row.hintsUsed() >= 2 ? row.bordersImageKey() : row.plainImageKey();
        int baseXp = row.baseXp();
        int penalty = row.hintPenaltyPercent();
        int maximumXp = row.alreadyRewarded() ? 0
                : Math.max(0, baseXp * (100 - penalty * row.hintsUsed()) / 100);
        int cluePenaltyXp = Math.min(maximumXp, baseXp * penalty / 100);
        String difficulty = row.rankInCountry() == 1 ? "EASY" : row.rankInCountry() <= 3 ? "MEDIUM" : "HARD";
        return new RiverQuestionDto(row.id(), difficulty,
                mapUrl(imageKey), row.course(),
                row.revealedHints(), row.attempts(),
                row.hintsUsed(), maximumXp, cluePenaltyXp, baseXp, row.roundNumber());
    }

    private void ensureCurrentLegalAcceptance(long userId) {
        UserRow user = authRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player is unavailable"));
        if (!legalAcceptanceService.isCurrent(user.termsVersionAccepted(), user.privacyVersionAccepted())) {
            throw new LegalAcceptanceRequiredException();
        }
    }
}
