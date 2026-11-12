package io.clueterra.api.game.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.clueterra.api.auth.repository.AuthJdbcRepository;
import io.clueterra.api.auth.repository.UserRow;
import io.clueterra.api.common.enums.SessionStatus;
import io.clueterra.api.common.utils.redis.RedisHelper;
import io.clueterra.api.exception.GameException;
import io.clueterra.api.game.dto.CountryDto;
import io.clueterra.api.game.dto.GameResultsDto;
import io.clueterra.api.game.dto.GameResultsCommunityStatsDto;
import io.clueterra.api.game.dto.GuessAttemptDto;
import io.clueterra.api.game.dto.HardestCountryInsightDto;
import io.clueterra.api.game.dto.HintDto;
import io.clueterra.api.game.dto.ResultCountryDto;
import io.clueterra.api.game.repository.GameSessionJdbcRepository;
import io.clueterra.api.game.repository.GameSessionJdbcRepository.CommunityStatsRow;
import io.clueterra.api.game.repository.GameSessionJdbcRepository.HardestCountryRow;
import io.clueterra.api.game.repository.GameSessionJdbcRepository.GuessAttemptRow;
import io.clueterra.api.game.repository.SessionStreakData;
import io.clueterra.api.game.repository.SessionCountryRow;
import io.clueterra.api.game.repository.SessionRow;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GameResultsService {

    private static final int MIN_COMMUNITY_SAMPLE_SIZE = 3;
    private static final String RESULTS_CACHE_PREFIX = "game:results:";
    private static final String COMMUNITY_STATS_CACHE_PREFIX = "game:community-stats:";
    private static final Duration COMMUNITY_STATS_TTL = Duration.ofMinutes(5);

    private final GameSessionJdbcRepository sessionRepository;
    private final ChallengeHintParser hintParser;
    private final RedisHelper redisHelper;
    private final Clock clock;
    private final AuthJdbcRepository authRepository;

    @Transactional(readOnly = true)
    public GameResultsDto getTodayResults(Long userId) {
        LocalDate today = LocalDate.now(clock);
        SessionRow session = resolveTodaySession(userId, today);

        if (session.status() != SessionStatus.COMPLETED) {
            throw GameException.resultsUnavailable();
        }

        String resultsCacheKey = RESULTS_CACHE_PREFIX + session.id();
        Optional<GameResultsDto> cached = redisHelper.read(resultsCacheKey, GameResultsDto.class);
        if (cached.isPresent()) {
            GameResultsCommunityStatsDto freshCommunity = getCachedCommunityStats(session.challengeLevel(), today);
            return cached.get().withGuestAndCommunityStats(isGuestSession(session), freshCommunity);
        }

        List<GuessAttemptRow> attempts = sessionRepository.findGuessAttemptsForSession(session.id());
        Map<Long, List<GuessAttemptRow>> attemptsByCountry = attempts.stream()
                .collect(Collectors.groupingBy(GuessAttemptRow::sessionCountryId));

        List<ResultCountryDto> countries = session.countries().stream()
                .map(country -> toResultCountry(session, country, attemptsByCountry.getOrDefault(country.id(), List.of())))
                .toList();

        int guessedCountries = (int) session.countries().stream().filter(SessionCountryRow::isGuessed).count();
        long activeTimeMs = session.countries().stream().mapToLong(SessionCountryRow::timeSpentMs).sum();
        GameResultsCommunityStatsDto communityStats = getCachedCommunityStats(session.challengeLevel(), today);

        SessionStreakData streak = sessionRepository.findSessionStreakData(session.id())
                .orElse(new SessionStreakData(0, 0));

        GameResultsDto results = new GameResultsDto(
                session.id(), session.challengeLevel(), session.status(), session.startedAt(),
                activeTimeMs, session.completedAt(), session.totalXpEarned(), session.xpBefore(),
                session.levelBefore(), session.countries().size(), guessedCountries,
                session.maxAttempts(), isGuestSession(session),
                streak.streakBonusXp(), countries, communityStats);

        redisHelper.write(resultsCacheKey, results, ttlUntilMidnight());
        return results;
    }

    private GameResultsCommunityStatsDto getCachedCommunityStats(String challengeLevel, LocalDate today) {
        String cacheKey = COMMUNITY_STATS_CACHE_PREFIX + challengeLevel + ":" + today;
        return redisHelper.read(cacheKey, GameResultsCommunityStatsDto.class)
                .orElseGet(() -> {
                    GameResultsCommunityStatsDto stats = buildCommunityStats(challengeLevel, today);
                    redisHelper.write(cacheKey, stats, COMMUNITY_STATS_TTL);
                    return stats;
                });
    }

    private Duration ttlUntilMidnight() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT);
        Duration ttl = Duration.between(now, midnight);
        return ttl.isNegative() || ttl.isZero() ? Duration.ofMinutes(1) : ttl;
    }

    private SessionRow resolveTodaySession(Long userId, LocalDate today) {
        if (userId == null) {
            throw GameException.todaySessionNotFound();
        }

        SessionRow session = sessionRepository.findTodaySessionForUser(userId).orElseThrow(GameException::todaySessionNotFound);

        if (!today.equals(session.challengeDate())) {
            throw GameException.todaySessionNotFound();
        }
        return session;
    }

    private boolean isGuestSession(SessionRow session) {
        if (session.userId() == null) {
            return true;
        }
        return authRepository.findById(session.userId()).map(UserRow::isGuest).orElse(false);
    }

    private ResultCountryDto toResultCountry(SessionRow session, SessionCountryRow country, List<GuessAttemptRow> attempts) {
        int hintsSeen = calculateHintsSeen(country, attempts);
        List<HintDto> hints = hintParser.getHintsForCountry(session.hintsJson(), country.countryOrder(), hintsSeen);
        List<GuessAttemptDto> attemptDtos = attempts.stream()
                .map(this::toGuessAttemptDto)
                .toList();

        return new ResultCountryDto(
                country.id(), country.countryOrder(), country.totalHints(), hintsSeen,
                country.isGuessed(), country.xpEarned(), toCountryDto(country), hints, attemptDtos);
    }

    private int calculateHintsSeen(SessionCountryRow country, List<GuessAttemptRow> attempts) {
        int hintsSeen = attempts.stream()
                .mapToInt(GuessAttemptRow::hintsAtGuess)
                .max()
                .orElse(country.hintsRevealed());
        return Math.max(0, Math.min(country.totalHints(), hintsSeen));
    }

    private GuessAttemptDto toGuessAttemptDto(GuessAttemptRow attempt) {
        return new GuessAttemptDto(
                attempt.attemptNumber(),
                new CountryDto(
                        attempt.guessedCountryId(), attempt.isoAlpha2(), attempt.nameEn(),
                        attempt.flagEmoji(), attempt.flagUrl(), attempt.region()),
                attempt.correct(),
                attempt.hintsAtGuess(),
                attempt.createdAt());
    }

    private CountryDto toCountryDto(SessionCountryRow country) {
        return new CountryDto(
                country.countryId(), country.isoAlpha2(), country.nameEn(),
                country.flagEmoji(), country.flagUrl(), country.region());
    }

    private GameResultsCommunityStatsDto buildCommunityStats(String challengeLevel, LocalDate today) {
        CommunityStatsRow row = sessionRepository.findTodayCommunityStats(challengeLevel, today);
        boolean enoughData = row.completedSessionsCount() >= MIN_COMMUNITY_SAMPLE_SIZE;

        HardestCountryInsightDto hardestCountry = enoughData
                ? sessionRepository.findTodayHardestCountry(challengeLevel, today, MIN_COMMUNITY_SAMPLE_SIZE)
                .map(this::toHardestCountryInsight)
                .orElse(null)
                : null;

        return new GameResultsCommunityStatsDto(
                enoughData,
                row.avgXpPerCompletedRun(),
                row.accuracyRate(),
                row.avgActiveTimePerSessionMs(),
                row.avgAttemptsPerCountry(),
                row.avgHintsPerCountry(),
                hardestCountry);
    }

    private HardestCountryInsightDto toHardestCountryInsight(HardestCountryRow row) {
        CountryDto confusedWith = row.confusedCountryId() != null
                ? new CountryDto(
                row.confusedCountryId(),
                row.confusedIsoAlpha2(),
                row.confusedNameEn(),
                row.confusedFlagEmoji(),
                row.confusedFlagUrl(),
                row.confusedRegion())
                : null;

        return new HardestCountryInsightDto(
                new CountryDto(
                        row.countryId(),
                        row.isoAlpha2(),
                        row.nameEn(),
                        row.flagEmoji(),
                        row.flagUrl(),
                        row.region()),
                row.successRate(),
                row.avgAttemptsNeeded(),
                row.avgHintsNeeded(),
                row.avgActiveTimeMs(),
                confusedWith);
    }
}
