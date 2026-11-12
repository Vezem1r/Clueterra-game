package io.clueterra.api.game.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.clueterra.api.admin.internal.questions.service.ClueterraQuestionsClient;
import io.clueterra.api.admin.settings.service.SettingsService;
import io.clueterra.api.auth.repository.AuthJdbcRepository;
import io.clueterra.api.auth.repository.UserRow;
import io.clueterra.api.common.enums.ChallengeLevel;
import io.clueterra.api.common.enums.SessionStatus;
import io.clueterra.api.common.utils.redis.RedisHelper;
import io.clueterra.api.exception.GameException;
import io.clueterra.api.game.dto.ChallengeDto;
import io.clueterra.api.game.dto.ChallengeMetadata;
import io.clueterra.api.game.dto.CountryDto;
import io.clueterra.api.game.dto.GameSessionDto;
import io.clueterra.api.game.dto.GameSessionSummaryDto;
import io.clueterra.api.game.dto.GuestProgressDto;
import io.clueterra.api.game.dto.GuestStateDto;
import io.clueterra.api.game.dto.HintDto;
import io.clueterra.api.game.dto.SessionCountryDto;
import io.clueterra.api.game.repository.ChallengeJdbcRepository;
import io.clueterra.api.game.repository.ChallengeJdbcRepository.ChallengeRow;
import io.clueterra.api.game.repository.ChallengeJdbcRepository.ChallengeSummaryRow;
import io.clueterra.api.game.repository.GameSessionJdbcRepository;
import io.clueterra.api.game.repository.SessionCountryRow;
import io.clueterra.api.game.repository.SessionRow;
import io.clueterra.api.legal.service.LegalAcceptanceService;
import io.clueterra.api.security.UserPrincipal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameSessionService {

    private static final String CHALLENGE_CACHE_PREFIX = "challenge:today:";
    private static final String CHALLENGE_SUMMARIES_CACHE_KEY = "challenge:today:summaries:";
    private static final TypeReference<List<ChallengeDto>> CHALLENGE_DTO_LIST_TYPE = new TypeReference<>() {
    };

    private final GameSessionJdbcRepository sessionRepository;
    private final ChallengeJdbcRepository challengeRepository;
    private final ChallengeHintParser hintParser;
    private final SettingsService settingsService;
    private final GameCompletionService completionService;
    private final ClueterraQuestionsClient questionsClient;
    private final RedisHelper redisHelper;
    private final Clock clock;
    private final LegalAcceptanceService legalAcceptanceService;
    private final AuthJdbcRepository authRepository;

    @Transactional(readOnly = true)
    public List<ChallengeDto> getTodaysChallenges(Long userId) {
        LocalDate today = LocalDate.now(clock);
        boolean hasPlayed = userId != null && sessionRepository.existsParticipation(userId, today);

        String cacheKey = CHALLENGE_SUMMARIES_CACHE_KEY + today;
        List<ChallengeDto> dtos = redisHelper.read(cacheKey, CHALLENGE_DTO_LIST_TYPE)
                .filter(cached -> !cached.isEmpty())
                .orElseGet(() -> loadTodayChallengeDtos(today, cacheKey));

        if (!hasPlayed) {
            return dtos;
        }

        return dtos.stream()
                .map(d -> new ChallengeDto(d.id(), d.date(), d.level(), d.difficulty(),
                        d.totalCountries(), d.totalHints(), false,
                        d.continentCount(), d.dataSourceCount(), d.categoryCount(), d.avgXp()))
                .toList();
    }

    private List<ChallengeDto> loadTodayChallengeDtos(LocalDate today, String cacheKey) {
        List<ChallengeDto> computed = challengeRepository.findActiveSummariesByDate(today)
                .stream()
                .map(this::toChallengeDto)
                .toList();

        if (computed.isEmpty()) {
            triggerChallengeGeneration();
            return List.of();
        }

        redisHelper.write(cacheKey, computed, ttlUntilMidnight());
        return computed;
    }

    public ChallengeDto getTodaysEasyChallenge() {
        LocalDate today = LocalDate.now(clock);
        ChallengeSummaryRow challenge = challengeRepository.findSummaryByDateAndLevel(today, ChallengeLevel.EASY.name())
                .orElseThrow(() -> {
                    triggerChallengeGeneration();
                    return GameException.challengeNotFound();
                });
        return toChallengeDto(challenge);
    }

    private ChallengeDto toChallengeDto(ChallengeSummaryRow c) {
        ChallengeMetadata meta = hintParser.extractMetadata(c.hintsJson());
        int avgXp = c.avgXp() != null ? c.avgXp() : estimateAverageChallengeXp(c.challengeLevel(), c.totalCountries(), c.totalHints());

        return new ChallengeDto(
                c.id(), c.challengeDate(), c.challengeLevel(),
                c.difficulty(), c.totalCountries(), c.totalHints(), true,
                c.continentCount(), meta.dataSourceCount(), meta.categoryCount(),
                avgXp);
    }

    private int estimateAverageChallengeXp(String challengeLevel, int totalCountries, int totalHints) {
        int safeTotalCountries = Math.max(1, totalCountries);
        int maxHintsPerCountry = Math.max(1, (int) Math.round((double) totalHints / safeTotalCountries));
        int maxAttempts = Math.max(1, settingsService.getMaxAttempts());
        long thresholdMs = Math.max(1L, settingsService.getTimeBonusThreshold() * 1000L);
        long[] timeSamples = {
                Math.max(1L, Math.round(thresholdMs * 0.4)),
                Math.max(1L, Math.round(thresholdMs * 0.65)),
                thresholdMs
        };

        long totalXp = 0L;
        int sampleCount = 0;

        for (int hintsRevealed = 0; hintsRevealed <= maxHintsPerCountry; hintsRevealed++) {
            for (int attemptsUsed = 1; attemptsUsed <= maxAttempts; attemptsUsed++) {
                for (long timeSpentMs : timeSamples) {
                    totalXp += completionService.calculateXp(
                            challengeLevel,
                            hintsRevealed,
                            attemptsUsed,
                            timeSpentMs,
                            maxHintsPerCountry,
                            maxAttempts);
                    sampleCount++;
                }
            }
        }

        int averagePerCountry = sampleCount == 0
                ? settingsService.getBaseXp(challengeLevel)
                : (int) Math.round((double) totalXp / sampleCount);

        return averagePerCountry * safeTotalCountries;
    }

    @Transactional
    public GameSessionDto startGuestSession(String termsVersion, String privacyVersion, String existingGuestToken) {
        ChallengeRow challenge = findCachedChallenge(ChallengeLevel.EASY);
        legalAcceptanceService.validateSubmittedVersions(termsVersion, privacyVersion);

        LocalDate today = LocalDate.now(clock);
        ResolvedGuestUser shadowUser = resolveShadowUser(existingGuestToken, termsVersion, privacyVersion);
        Long shadowUserId = shadowUser.userId();
        if (shadowUser.created() || !legalAcceptanceService.isCurrent(shadowUser.termsVersionAccepted(), shadowUser.privacyVersionAccepted())) {
            legalAcceptanceService.recordUserAcceptance(shadowUserId, termsVersion, privacyVersion, LegalAcceptanceService.SOURCE_GUEST_START);
        }

        if (sessionRepository.existsParticipation(shadowUserId, today)) {
            SessionRow existing = sessionRepository.findTodaySessionForUser(shadowUserId).orElse(null);
            if (existing != null && existing.status() == SessionStatus.IN_PROGRESS) {
                log.info("Guest session reused: sessionId={}, userId={}", existing.id(), shadowUserId);
                return toSessionDto(existing);
            }
            throw GameException.alreadyPlayedToday();
        }

        int maxHints = hintParser.countHintsPerCountry(challenge.hintsJson());
        long sessionId = sessionRepository.insertSession(
                shadowUserId, null, challenge.id(), challenge.challengeLevel(),
                maxHints, settingsService.getMaxAttempts(), termsVersion, privacyVersion, Instant.now(clock));
        initializeSessionCountries(sessionId, challenge);
        int inserted = sessionRepository.insertParticipation(shadowUserId, today, ChallengeLevel.EASY.name(), sessionId);
        if (inserted == 0) {
            throw GameException.alreadyPlayedToday();
        }
        log.info("Guest session created: sessionId={}, userId={}", sessionId, shadowUserId);
        return getSessionDto(sessionId);
    }

    private ResolvedGuestUser resolveShadowUser(String existingGuestToken, String termsVersion, String privacyVersion) {
        if (existingGuestToken != null && !existingGuestToken.isBlank()) {
            Optional<UserRow> existing = authRepository.findByGuestToken(existingGuestToken);
            if (existing.isPresent()) {
                UserRow user = existing.get();
                authRepository.refreshGuestTokenExpiry(user.id());
                return new ResolvedGuestUser(
                        user.id(), false, user.termsVersionAccepted(), user.privacyVersionAccepted());
            }
        }
        long userId = authRepository.insertGuestUser(termsVersion, privacyVersion);
        return new ResolvedGuestUser(userId, true, termsVersion, privacyVersion);
    }

    public String getGuestTokenForSession(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .map(SessionRow::userId)
                .flatMap(authRepository::findById)
                .filter(UserRow::isGuest)
                .map(UserRow::guestToken)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Optional<GameSessionDto> getTodaySession(Long userId) {
        return sessionRepository.findTodaySessionForUser(userId).map(this::toSessionDto);
    }

    @Transactional(readOnly = true)
    public Optional<GameSessionSummaryDto> getTodaySessionSummary(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return sessionRepository.findTodaySummaryForUser(userId, LocalDate.now(clock)).map(this::toSummaryDto);
    }

    @Transactional(readOnly = true)
    public GuestProgressDto getGuestProgress(UserPrincipal principal) {
        if (principal == null || !principal.isGuest()) {
            return GuestProgressDto.none();
        }

        return sessionRepository.findGuestProgressForUser(principal.getId())
                .map(row -> new GuestProgressDto(
                        true,
                        row.playCount(),
                        row.budgetPlayCount(),
                        row.totalXp(),
                        row.currentLevel(),
                        row.levelDivisor(),
                        row.currentStreak(),
                        row.bestStreak(),
                        row.badgesEarned(),
                        row.expiresAt()))
                .orElseGet(GuestProgressDto::none);
    }

    @Transactional(readOnly = true)
    public GuestStateDto getGuestState(UserPrincipal principal) {
        if (principal == null || !principal.isGuest()) {
            return GuestStateDto.none();
        }

        Optional<SessionRow> todaySession = sessionRepository.findTodaySessionForUser(principal.getId());
        return new GuestStateDto(
                true,
                legalAcceptanceService.requiresAcceptance(principal),
                todaySession.isPresent(),
                todaySession.map(SessionRow::id).orElse(null),
                todaySession.map(SessionRow::status).orElse(null));
    }

    @Transactional
    public GameSessionDto startUserSession(Long userId, ChallengeLevel level) {
        LocalDate today = LocalDate.now(clock);
        if (sessionRepository.existsParticipation(userId, today)) {
            SessionRow existing = sessionRepository.findTodaySessionForUser(userId).orElse(null);
            if (existing != null && existing.status() == SessionStatus.IN_PROGRESS) {
                return toSessionDto(existing);
            }
            throw GameException.alreadyPlayedToday();
        }

        ChallengeRow challenge = findCachedChallenge(level);

        int maxHints = hintParser.countHintsPerCountry(challenge.hintsJson());
        long sessionId = sessionRepository.insertSession(
                userId, null, challenge.id(), level.name(),
                maxHints, settingsService.getMaxAttempts(), null, null, null);
        initializeSessionCountries(sessionId, challenge);
        int inserted = sessionRepository.insertParticipation(userId, today, level.name(), sessionId);
        if (inserted == 0) {
            // Race condition: another concurrent request won - roll back this transaction
            throw GameException.alreadyPlayedToday();
        }

        log.info("User session created: sessionId={}, userId={}, level={}", sessionId, userId, level);
        return getSessionDto(sessionId);
    }

    @Transactional(readOnly = true)
    public GameSessionDto getSession(Long sessionId, Long userId) {
        SessionRow session = sessionRepository.findById(sessionId).orElseThrow(() -> GameException.sessionNotFound(sessionId));
        validateAccess(session, userId);
        return toSessionDto(session);
    }

    public void validateAccess(SessionRow session, Long userId) {
        if (userId != null && userId.equals(session.userId())) {
            return;
        }
        throw GameException.sessionAccessDenied();
    }

    public GameSessionDto getSessionDto(long sessionId) {
        SessionRow session = sessionRepository.findById(sessionId).orElseThrow(() -> GameException.sessionNotFound(sessionId));
        return toSessionDto(session);
    }

    public GameSessionDto toSessionDto(SessionRow session) {
        int maxAttempts = session.maxAttempts();
        int maxHints = session.maxHintsPerCountry();
        boolean inProgress = session.status() == SessionStatus.IN_PROGRESS;
        int currentIdx = session.currentCountryIdx();
        int totalXpEarned = inProgress
                ? session.countries().stream().mapToInt(SessionCountryRow::xpEarned).sum()
                : session.totalXpEarned();
        List<SessionCountryDto> countryDtos = new ArrayList<>();

        for (SessionCountryRow sc : session.countries()) {
            boolean isFutureUnplayed = inProgress && sc.countryOrder() > currentIdx + 1;
            List<HintDto> hints = isFutureUnplayed
                    ? List.of()
                    : hintParser.getHintsForCountry(session.hintsJson(), sc.countryOrder(), sc.hintsRevealed());

            CountryDto correctCountry = null;
            boolean canGuess = !sc.isGuessed() && sc.attemptsUsed() < maxAttempts;
            if (sc.isGuessed() || !canGuess) {
                correctCountry = new CountryDto(sc.countryId(), sc.isoAlpha2(), sc.nameEn(), sc.flagEmoji(), sc.flagUrl(), sc.region());
            }

            countryDtos.add(new SessionCountryDto(
                    sc.id(), sc.countryOrder(), sc.totalHints(), sc.hintsRevealed(),
                    maxAttempts - sc.attemptsUsed(), sc.isGuessed(),
                    sc.xpEarned(), hints, correctCountry));
        }

        return new GameSessionDto(
                session.id(), session.guestToken(), isGuestSession(session), session.challengeLevel(),
                session.status(), session.currentCountryIdx(),
                session.countries().size(), maxHints,
                maxAttempts, session.startedAt(), sumActiveTimeMs(session), session.completedAt(),
                totalXpEarned, session.xpBefore(), session.levelBefore(), countryDtos);
    }

    private boolean isGuestSession(SessionRow session) {
        if (session.userId() == null) {
            return true;
        }
        return authRepository.findById(session.userId())
                .map(UserRow::isGuest)
                .orElse(false);
    }

    private GameSessionSummaryDto toSummaryDto(GameSessionJdbcRepository.SessionSummaryRow row) {
        return new GameSessionSummaryDto(
                row.id(), row.challengeLevel(), row.status(), row.currentCountryIdx(),
                row.totalCountries(), row.guessedCountries(), row.totalHintsRevealed(), row.totalAttemptsUsed(),
                row.startedAt(), row.activeTimeMs(), row.completedAt(),
                row.totalXpEarned());
    }

    private void initializeSessionCountries(long sessionId, ChallengeRow challenge) {
        List<ChallengeHintParser.SessionCountrySeed> entries = hintParser.parseSessionCountries(
                challenge.countriesJson(),
                challenge.hintsJson());
        sessionRepository.insertSessionCountriesBatch(sessionId, entries);
    }

    private ChallengeRow findCachedChallenge(ChallengeLevel level) {
        LocalDate today = LocalDate.now(clock);
        String cacheKey = CHALLENGE_CACHE_PREFIX + today + ":" + level.name();

        Optional<ChallengeRow> cached = redisHelper.read(cacheKey, ChallengeRow.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        ChallengeRow challenge = challengeRepository.findByDateAndLevel(today, level.name())
                .orElseThrow(() -> {
                    triggerChallengeGeneration();
                    return GameException.challengeNotFound();
                });

        redisHelper.write(cacheKey, challenge, ttlUntilMidnight());
        return challenge;
    }

    private void triggerChallengeGeneration() {
        questionsClient.fireAndForget("Auto-generate today", "/api/admin/challenge/today");
    }

    private Duration ttlUntilMidnight() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT);
        Duration ttl = Duration.between(now, midnight);
        return ttl.isNegative() || ttl.isZero() ? Duration.ofMinutes(1) : ttl;
    }

    private long sumActiveTimeMs(SessionRow session) {
        return session.countries().stream()
                .mapToLong(SessionCountryRow::timeSpentMs)
                .sum();
    }
}
