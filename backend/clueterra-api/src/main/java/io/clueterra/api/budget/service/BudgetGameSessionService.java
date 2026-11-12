package io.clueterra.api.budget.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.clueterra.api.admin.settings.service.SettingsService;
import io.clueterra.api.achievements.engine.TemplateBadgeEngine;
import io.clueterra.api.auth.repository.AuthJdbcRepository;
import io.clueterra.api.auth.repository.UserRow;
import io.clueterra.api.budget.dto.BudgetImageDto;
import io.clueterra.api.budget.dto.BudgetAnswerRequest;
import io.clueterra.api.budget.dto.BudgetAnswerResultDto;
import io.clueterra.api.budget.dto.BudgetAnsweredQuestionDto;
import io.clueterra.api.budget.dto.BudgetChallengeQuestionDto;
import io.clueterra.api.budget.dto.BudgetGameSessionDto;
import io.clueterra.api.budget.dto.BudgetHintDto;
import io.clueterra.api.budget.dto.BudgetHintRequest;
import io.clueterra.api.budget.dto.BudgetHintResultDto;
import io.clueterra.api.budget.dto.BudgetTodayChallengeDto;
import io.clueterra.api.budget.exception.BudgetGameException;
import io.clueterra.api.budget.model.BudgetChallengeQuestionRow;
import io.clueterra.api.budget.model.BudgetGameAnswerRow;
import io.clueterra.api.budget.model.BudgetGameHintRow;
import io.clueterra.api.budget.model.BudgetGameSessionRow;
import io.clueterra.api.budget.model.BudgetHintMutationRow;
import io.clueterra.api.budget.model.BudgetQuestionImageRow;
import io.clueterra.api.budget.model.BudgetSessionMutationRow;
import io.clueterra.api.budget.model.BudgetTodayChallengeRow;
import io.clueterra.api.budget.repository.BudgetGameSessionJdbcRepository;
import io.clueterra.api.exception.LegalAcceptanceRequiredException;
import io.clueterra.api.legal.service.LegalAcceptanceService;
import io.clueterra.api.storage.StorageService;

import io.hypersistence.tsid.TSID;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BudgetGameSessionService {

    private static final int QUESTIONS_PER_CHALLENGE = 5;

    private final BudgetGameSessionJdbcRepository repository;
    private final SettingsService settingsService;
    private final AuthJdbcRepository authRepository;
    private final LegalAcceptanceService legalAcceptanceService;
    private final StorageService storageService;
    private final TemplateBadgeEngine templateBadgeEngine;
    private final BudgetRevealPayloadSanitizer revealPayloadSanitizer;
    private final Clock clock;

    @Transactional(readOnly = true)
    public BudgetTodayChallengeDto getTodayChallenge() {
        return repository.findTodayChallenge()
                .map(challenge -> new BudgetTodayChallengeDto(
                        challenge.date(), challenge.questionCount(), challenge.questionCount() == QUESTIONS_PER_CHALLENGE))
                .orElseGet(() -> new BudgetTodayChallengeDto(LocalDate.now(clock), 0, false));
    }

    @Transactional
    public BudgetGameSessionDto startGuestSession(
            String termsVersion, String privacyVersion, String guestToken) {
        ensureChallengeAvailable();
        legalAcceptanceService.validateSubmittedVersions(termsVersion, privacyVersion);

        UserRow guest = resolveGuest(guestToken, termsVersion, privacyVersion);
        if (!legalAcceptanceService.isCurrent(
                guest.termsVersionAccepted(), guest.privacyVersionAccepted())) {
            legalAcceptanceService.recordUserAcceptance(
                    guest.id(), termsVersion, privacyVersion, LegalAcceptanceService.SOURCE_GUEST_REACCEPT);
        }
        return startSession(guest.id());
    }

    @Transactional
    public BudgetGameSessionDto startUserSession(long userId) {
        ensureCurrentLegalAcceptance(userId);
        ensureChallengeAvailable();
        return startSession(userId);
    }

    @Transactional(readOnly = true)
    public Optional<BudgetGameSessionDto> getTodaySession(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return repository.findTodaySession(userId).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public BudgetGameSessionDto getSession(long sessionId, Long userId) {
        BudgetGameSessionRow session = repository.findSession(sessionId)
                .orElseThrow(() -> BudgetGameException.fromOutcome("SESSION_NOT_FOUND", sessionId));
        validateAccess(session, userId);
        validateToday(session);
        return toDto(session);
    }

    @Transactional
    public BudgetAnswerResultDto submitAnswer(
            long sessionId, Long userId, BudgetAnswerRequest request) {
        if (userId == null) {
            throw BudgetGameException.fromOutcome("ACCESS_DENIED", sessionId);
        }
        ensureCurrentLegalAcceptance(userId);
        BudgetSessionMutationRow mutation = repository.submitAnswer(
                TSID.Factory.getTsid().toLong(),
                sessionId,
                userId,
                request.variantId(),
                request.value(),
                request.choice());
        boolean replayed = "ALREADY_ANSWERED".equals(mutation.outcome());
        if (!replayed && !"ANSWERED".equals(mutation.outcome())) {
            throw BudgetGameException.fromOutcome(mutation.outcome(), sessionId);
        }

        BudgetGameSessionDto session = getSession(sessionId, userId);
        BudgetAnsweredQuestionDto answer = session.answeredQuestions().stream()
                .filter(candidate -> candidate.question().variantId() == request.variantId())
                .findFirst()
                .orElseThrow(() -> BudgetGameException.fromOutcome("SESSION_NOT_FOUND", sessionId));
        if (!replayed) {
            templateBadgeEngine.evaluateForUser(userId, null);
        }
        return new BudgetAnswerResultDto(replayed, answer, session);
    }

    @Transactional
    public BudgetHintResultDto revealHint(
            long sessionId, Long userId, BudgetHintRequest request) {
        if (userId == null) {
            throw BudgetGameException.fromOutcome("ACCESS_DENIED", sessionId);
        }
        ensureCurrentLegalAcceptance(userId);
        BudgetHintMutationRow mutation = repository.revealHint(
                sessionId, userId, request.variantId(), request.hintIndex());
        boolean replayed = "ALREADY_REVEALED".equals(mutation.outcome());
        if (!replayed && !"REVEALED".equals(mutation.outcome())) {
            throw BudgetGameException.fromOutcome(mutation.outcome(), sessionId);
        }

        BudgetHintDto hint = new BudgetHintDto(
                mutation.index(),
                mutation.payload().path("multiplierPercent").decimalValue(),
                mutation.payload(),
                mutation.revealedAt());
        return new BudgetHintResultDto(
                replayed,
                hint,
                mutation.effectiveMultiplierPercent(),
                getSession(sessionId, userId));
    }

    @Transactional(readOnly = true)
    public String getGuestToken(long sessionId) {
        return repository.findGuestToken(sessionId).orElse(null);
    }

    private BudgetGameSessionDto startSession(long userId) {
        BudgetSessionMutationRow mutation = repository.startSession(
                TSID.Factory.getTsid().toLong(),
                userId,
                settingsService.getBudgetBaseXpPerQuestion(),
                settingsService.getBudgetFullXpThresholdPercent(),
                settingsService.getBudgetXpCurvePower(),
                settingsService.getLevelDivisor());
        if (!"STARTED".equals(mutation.outcome()) && !"EXISTING".equals(mutation.outcome())) {
            throw BudgetGameException.fromOutcome(mutation.outcome(), mutation.sessionId());
        }
        return repository.findSession(mutation.sessionId())
                .map(this::toDto)
                .orElseThrow(() -> BudgetGameException.fromOutcome("SESSION_NOT_FOUND", mutation.sessionId()));
    }

    private UserRow resolveGuest(String guestToken, String termsVersion, String privacyVersion) {
        if (guestToken != null && !guestToken.isBlank()) {
            Optional<UserRow> existing = authRepository.findByGuestToken(guestToken);
            if (existing.isPresent()) {
                authRepository.refreshGuestTokenExpiry(existing.get().id());
                return existing.get();
            }
        }
        long guestUserId = authRepository.insertGuestUser(termsVersion, privacyVersion);
        UserRow guest = authRepository.findById(guestUserId)
                .orElseThrow(() -> BudgetGameException.fromOutcome("USER_NOT_FOUND", null));
        legalAcceptanceService.recordUserAcceptance(
                guestUserId, termsVersion, privacyVersion, LegalAcceptanceService.SOURCE_GUEST_START);
        return guest;
    }

    private void ensureChallengeAvailable() {
        BudgetTodayChallengeRow challenge = repository.findTodayChallenge()
                .orElseThrow(() -> BudgetGameException.fromOutcome("CHALLENGE_NOT_FOUND", null));
        if (challenge.questionCount() != QUESTIONS_PER_CHALLENGE) {
            throw BudgetGameException.fromOutcome("INVALID_CHALLENGE", null);
        }
    }

    private void ensureCurrentLegalAcceptance(long userId) {
        UserRow user = authRepository.findById(userId)
                .orElseThrow(() -> BudgetGameException.fromOutcome("USER_NOT_FOUND", userId));
        if (!legalAcceptanceService.isCurrent(
                user.termsVersionAccepted(), user.privacyVersionAccepted())) {
            throw new LegalAcceptanceRequiredException();
        }
    }

    private BudgetGameSessionDto toDto(BudgetGameSessionRow session) {
        List<BudgetChallengeQuestionRow> questions = repository.findChallengeQuestions(session.challengeId());
        if (questions.size() != QUESTIONS_PER_CHALLENGE) {
            throw BudgetGameException.fromOutcome("INVALID_CHALLENGE", session.id());
        }

        Map<Long, List<BudgetHintDto>> hintsByVariant = repository.findRevealedHints(session.id())
                .stream()
                .collect(Collectors.groupingBy(
                        BudgetGameHintRow::variantId,
                        Collectors.mapping(this::toHintDto, Collectors.toList())));
        Map<Long, BudgetChallengeQuestionDto> questionDtos = questions.stream()
                .map(question -> toQuestionDto(
                        question,
                        hintsByVariant.getOrDefault(question.variantId(), List.of())))
                .collect(Collectors.toUnmodifiableMap(BudgetChallengeQuestionDto::variantId, Function.identity()));
        List<BudgetAnsweredQuestionDto> answered = repository.findAnswers(session.id()).stream()
                .map(answer -> toAnsweredDto(answer, questionDtos))
                .toList();
        BudgetChallengeQuestionDto current = questions.stream()
                .filter(question -> question.position() == session.currentPosition())
                .map(question -> questionDtos.get(question.variantId()))
                .findFirst()
                .orElse(null);

        return new BudgetGameSessionDto(
                session.id(),
                session.challengeDate(),
                session.status(),
                session.guest(),
                session.currentPosition(),
                QUESTIONS_PER_CHALLENGE,
                session.totalXpEarned(),
                session.streakBonusXp(),
                session.baseXpPerQuestion() * QUESTIONS_PER_CHALLENGE,
                session.fullXpThresholdPercent(),
                session.xpBefore(),
                session.levelBefore(),
                session.startedAt(),
                session.completedAt(),
                answered,
                current);
    }

    private BudgetChallengeQuestionDto toQuestionDto(
            BudgetChallengeQuestionRow question, List<BudgetHintDto> revealedHints) {
        return new BudgetChallengeQuestionDto(
                question.variantId(),
                question.position(),
                question.type(),
                question.difficulty(),
                question.promptPayload(),
                question.availableHintCount(),
                revealedHints,
                image(question.variantId(), "primary", question.primaryImage()),
                image(question.variantId(), "comparison", question.comparisonImage()));
    }

    private BudgetAnsweredQuestionDto toAnsweredDto(
            BudgetGameAnswerRow answer, Map<Long, BudgetChallengeQuestionDto> questions) {
        BudgetChallengeQuestionDto question = questions.get(answer.variantId());
        if (question == null) {
            throw new IllegalStateException("BudgetGuessr answer references an unknown challenge question");
        }
        return new BudgetAnsweredQuestionDto(
                question,
                answer.guessedValue(),
                answer.guessedChoice(),
                answer.answerValue(),
                answer.answerUnit(),
                answer.answerChoice(),
                answer.accuracyPercent(),
                answer.rawXpPercent(),
                answer.rawXpEarned(),
                answer.hintMultiplierPercent(),
                answer.hintsUsed(),
                answer.xpPercent(),
                answer.xpEarned(),
                revealPayloadSanitizer.sanitize(answer.revealPayload()),
                answer.answeredAt());
    }

    private BudgetHintDto toHintDto(BudgetGameHintRow hint) {
        return new BudgetHintDto(
                hint.index(),
                hint.multiplierPercent(),
                hint.payload(),
                hint.revealedAt());
    }

    private BudgetImageDto image(long variantId, String role, BudgetQuestionImageRow image) {
        if (image == null) {
            return null;
        }
        return new BudgetImageDto(
                storageService.resolveBudgetVariantAssetUrl(variantId, role, image.objectKey()),
                image.altText(),
                image.authorName(),
                image.attributionText(),
                image.licenseLabel(),
                image.licenseUrl(),
                image.sourcePageUrl());
    }

    private void validateAccess(BudgetGameSessionRow session, Long userId) {
        if (userId == null || session.userId() != userId) {
            throw BudgetGameException.fromOutcome("ACCESS_DENIED", session.id());
        }
    }

    private void validateToday(BudgetGameSessionRow session) {
        if (!LocalDate.now(clock).equals(session.challengeDate())) {
            throw BudgetGameException.fromOutcome("SESSION_EXPIRED", session.id());
        }
    }
}
