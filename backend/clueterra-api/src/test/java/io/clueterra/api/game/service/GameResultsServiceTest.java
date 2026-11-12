package io.clueterra.api.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.clueterra.api.auth.repository.AuthJdbcRepository;
import io.clueterra.api.auth.repository.UserRow;
import io.clueterra.api.common.enums.SessionStatus;
import io.clueterra.api.exception.GameException;
import io.clueterra.api.game.dto.HintDto;
import io.clueterra.api.game.repository.GameSessionJdbcRepository;
import io.clueterra.api.game.repository.GameSessionJdbcRepository.CommunityStatsRow;
import io.clueterra.api.game.repository.GameSessionJdbcRepository.HardestCountryRow;
import io.clueterra.api.game.repository.GameSessionJdbcRepository.GuessAttemptRow;
import io.clueterra.api.game.repository.SessionStreakData;
import io.clueterra.api.common.utils.redis.RedisHelper;
import io.clueterra.api.game.repository.SessionCountryRow;
import io.clueterra.api.game.repository.SessionRow;

@ExtendWith(MockitoExtension.class)
class GameResultsServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2025-03-13T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2025, 3, 13);
    private static final long SESSION_ID = 1L;

    @Mock
    private GameSessionJdbcRepository sessionRepository;
    @Mock
    private ChallengeHintParser hintParser;
    @Mock
    private RedisHelper redisHelper;
    @Mock
    private AuthJdbcRepository authRepository;

    private GameResultsService service;

    @BeforeEach
    void setUp() {
        service = new GameResultsService(
                sessionRepository, hintParser, redisHelper, CLOCK, authRepository);
        lenient().when(sessionRepository.findTodayCommunityStats("EASY", TODAY))
                .thenReturn(new CommunityStatsRow(1, 120, 80.0, 42_000L, 1.3, 3.2));
        lenient().when(sessionRepository.findSessionStreakData(SESSION_ID))
                .thenReturn(Optional.of(new SessionStreakData(0, 0)));
    }

    @Test
    @DisplayName("shadow guest results include only hints visible before attempts")
    void shadowGuestResults_useAttemptHintsAsVisibleHintLimit() {
        SessionRow session = completedUserSession(TODAY, List.of(country(3, true, 120)));
        GuessAttemptRow first = attempt(1, 1, false, 99L, "FR");
        GuessAttemptRow second = attempt(2, 2, true, 42L, "DE");
        HintDto visibleHint = new HintDto(10L, "Seen clue", 50, "geo", "text", "wiki", null);

        when(sessionRepository.findTodaySessionForUser(42L)).thenReturn(Optional.of(session));
        when(authRepository.findById(42L)).thenReturn(Optional.of(userRow(true)));
        when(sessionRepository.findGuessAttemptsForSession(SESSION_ID)).thenReturn(List.of(first, second));
        when(hintParser.getHintsForCountry("hints-json", 1, 2)).thenReturn(List.of(visibleHint));

        var results = service.getTodayResults(42L);

        verify(hintParser).getHintsForCountry("hints-json", 1, 2);
        assertThat(results.guest()).isTrue();
        assertThat(results.guessedCountries()).isEqualTo(1);
        assertThat(results.countries()).hasSize(1);
        assertThat(results.countries().getFirst().hintsSeen()).isEqualTo(2);
        assertThat(results.countries().getFirst().hints()).containsExactly(visibleHint);
        assertThat(results.countries().getFirst().attempts()).hasSize(2);
        assertThat(results.countries().getFirst().attempts().getFirst().guessedCountry().code()).isEqualTo("FR");
        assertThat(results.activeTimeMs()).isEqualTo(18_000L);
        assertThat(results.communityStats().enoughData()).isFalse();
    }

    @Test
    @DisplayName("guest results are unavailable for old sessions")
    void guestResults_oldSessionIsNotVisible() {
        SessionRow oldSession = completedUserSession(TODAY.minusDays(1), List.of(country(1, true, 100)));
        when(sessionRepository.findTodaySessionForUser(42L)).thenReturn(Optional.of(oldSession));

        assertThatThrownBy(() -> service.getTodayResults(42L))
                .isInstanceOf(GameException.class)
                .extracting("errorCode").isEqualTo("TODAY_SESSION_NOT_FOUND");

        verify(sessionRepository, never()).findGuessAttemptsForSession(anyLong());
    }

    @Test
    @DisplayName("shadow guest results remain marked as guest")
    void shadowGuestResults_areMarkedGuest() {
        SessionRow session = completedUserSession(TODAY, List.of(country(1, true, 100)));
        when(sessionRepository.findTodaySessionForUser(42L)).thenReturn(Optional.of(session));
        when(authRepository.findById(42L)).thenReturn(Optional.of(userRow(true)));
        when(sessionRepository.findGuessAttemptsForSession(SESSION_ID)).thenReturn(List.of(attempt(1, 1, true, 42L, "DE")));
        when(hintParser.getHintsForCountry("hints-json", 1, 1)).thenReturn(List.of());

        var results = service.getTodayResults(42L);

        assertThat(results.guest()).isTrue();
    }

    @Test
    @DisplayName("auth results use today's user participation without client session id")
    void authResults_loadsTodayUserSession() {
        SessionRow session = completedUserSession(TODAY, List.of(country(1, true, 100)));
        when(sessionRepository.findTodaySessionForUser(42L)).thenReturn(Optional.of(session));
        when(authRepository.findById(42L)).thenReturn(Optional.of(userRow(false)));
        when(sessionRepository.findGuessAttemptsForSession(SESSION_ID)).thenReturn(List.of(attempt(1, 1, true, 42L, "DE")));
        when(hintParser.getHintsForCountry("hints-json", 1, 1)).thenReturn(List.of());

        var results = service.getTodayResults(42L);

        assertThat(results.id()).isEqualTo(SESSION_ID);
        assertThat(results.guest()).isFalse();
        assertThat(results.communityStats().avgXpPerCompletedRun()).isEqualTo(120);
        verify(sessionRepository).findTodaySessionForUser(42L);
    }

    @Test
    @DisplayName("community stats include hardest country insight when enough completed runs exist")
    void results_includeCommunityInsight() {
        SessionRow session = completedUserSession(TODAY, List.of(country(1, true, 100)));
        when(sessionRepository.findTodaySessionForUser(42L)).thenReturn(Optional.of(session));
        when(authRepository.findById(42L)).thenReturn(Optional.of(userRow(false)));
        when(sessionRepository.findGuessAttemptsForSession(SESSION_ID)).thenReturn(List.of(attempt(1, 1, true, 42L, "DE")));
        when(hintParser.getHintsForCountry("hints-json", 1, 1)).thenReturn(List.of());
        when(sessionRepository.findTodayCommunityStats("EASY", TODAY))
                .thenReturn(new CommunityStatsRow(3, 148, 74.5, 61_000L, 1.7, 4.1));
        when(sessionRepository.findTodayHardestCountry("EASY", TODAY, 3))
                .thenReturn(Optional.of(new HardestCountryRow(
                        55L, "JP", "Japan", "🇯🇵", "flag-url", "Asia",
                        41.7, 2.4, 4.8, 73_000L,
                        56L, "KR", "South Korea", "🇰🇷", "flag-url-2", "Asia")));

        var results = service.getTodayResults(42L);

        assertThat(results.communityStats().enoughData()).isTrue();
        assertThat(results.communityStats().hardestCountry()).isNotNull();
        assertThat(results.communityStats().hardestCountry().country().name()).isEqualTo("Japan");
        assertThat(results.communityStats().hardestCountry().mostConfusedWith()).isNotNull();
        assertThat(results.communityStats().hardestCountry().mostConfusedWith().code()).isEqualTo("KR");
    }

    @Test
    @DisplayName("in-progress sessions do not expose results")
    void inProgressSession_rejectsResults() {
        SessionRow session = new SessionRow(
                SESSION_ID, 42L, null, 10L, TODAY, "EASY", SessionStatus.IN_PROGRESS, 0,
                Instant.now(), Instant.now(), null, 0, 3, 3, 0L, 1, null, null, "hints-json", List.of(country(1, false, 0)));
        when(sessionRepository.findTodaySessionForUser(42L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.getTodayResults(42L))
                .isInstanceOf(GameException.class)
                .extracting("errorCode").isEqualTo("RESULTS_UNAVAILABLE");
    }

    private static SessionRow completedUserSession(LocalDate challengeDate, List<SessionCountryRow> countries) {
        return new SessionRow(
                SESSION_ID, 42L, null, 10L, challengeDate, "EASY", SessionStatus.COMPLETED, 0,
                Instant.now(), Instant.now(), Instant.now(), 100, 3, 3, 0L, 1, null, null, "hints-json", countries);
    }

    private static SessionCountryRow country(int hintsRevealed, boolean guessed, int xpEarned) {
        return new SessionCountryRow(
                11L, 42L, 1, 3, hintsRevealed, guessed ? 1 : 3, guessed, 18_000L, xpEarned,
                "DE", "Germany", "DE", "flag", "Europe");
    }

    private static GuessAttemptRow attempt(int attemptNumber, int hintsAtGuess, boolean correct, long countryId, String code) {
        return new GuessAttemptRow(
                11L, attemptNumber, hintsAtGuess, correct, Instant.now(),
                countryId, code, code.equals("DE") ? "Germany" : "France", "flag", "flag-url", "Europe");
    }

    private static UserRow userRow(boolean isGuest) {
        return new UserRow(
                42L, "user@example.com", "hash", "player", "USER", true,
                null, false, null, null, "2026-04-21", "2026-04-21", isGuest, "guest-token");
    }
}
