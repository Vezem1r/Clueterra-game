package io.clueterra.api.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

@ExtendWith(MockitoExtension.class)
class GameplayServiceTest {

    @Mock
    private GameSessionJdbcRepository sessionRepository;
    @Mock
    private CountrySearchJdbcRepository countrySearchRepository;
    @Mock
    private GameSessionService ignored;
    @Mock
    private GameCompletionService completionService;
    @Mock
    private ChallengeHintParser hintParser;
    @Mock
    private SessionActivityService activityService;

    @InjectMocks
    private GameplayService gameplayService;

    private static final long SESSION_ID = 1L;
    private static final long USER_ID = 100L;
    private static final long CORRECT_COUNTRY_ID = 42L;
    private static final long WRONG_COUNTRY_ID = 99L;
    private static final Instant ACTIVITY_AT = Instant.parse("2025-03-13T10:00:05Z");
    private static final long ACTIVE_TIME_MS = 5_000L;

    @BeforeEach
    void setUp() {
        lenient().when(activityService.capture(any(), any()))
                .thenReturn(new SessionActivityService.ActivitySnapshot(ACTIVITY_AT, ACTIVE_TIME_MS));
    }

    @Nested
    @DisplayName("guess - correct answer")
    class GuessCorrect {

        @Test
        @DisplayName("correct guess returns isCorrect=true with XP and country info")
        void correctGuess_returnsSuccessResult() {
            SessionRow session = session("EASY", List.of(country(0, 0, false)));
            CountryDto guessedCountry = countryDto(CORRECT_COUNTRY_ID, "DE");
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
            when(countrySearchRepository.findByCode("DE")).thenReturn(Optional.of(guessedCountry));
            when(completionService.calculateXp(any(), anyInt(), anyInt(), anyLong(), anyInt(), anyInt()))
                    .thenReturn(150);

            GuessResultDto result = gameplayService.guess(SESSION_ID, "DE", USER_ID);

            assertThat(result.correct()).isTrue();
            assertThat(result.xpEarned()).isEqualTo(150);
            assertThat(result.countryComplete()).isTrue();
            assertThat(result.correctCountry()).isNotNull();
            assertThat(result.correctCountry().code()).isEqualTo("DE");
            verify(completionService).calculateXp(any(), anyInt(), anyInt(), eq(ACTIVE_TIME_MS), anyInt(), anyInt());
            verify(sessionRepository).updateSessionActivity(SESSION_ID, ACTIVITY_AT);
        }

        @Test
        @DisplayName("correct guess on single-country session marks the whole session complete")
        void correctGuessOnLastCountry_completesSession() {
            SessionRow session = session("EASY", List.of(country(0, 0, false)));
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
            when(countrySearchRepository.findByCode("DE"))
                    .thenReturn(Optional.of(countryDto(CORRECT_COUNTRY_ID, "DE")));
            when(completionService.calculateXp(any(), anyInt(), anyInt(), anyLong(), anyInt(), anyInt()))
                    .thenReturn(100);

            GuessResultDto result = gameplayService.guess(SESSION_ID, "DE", USER_ID);

            assertThat(result.sessionComplete()).isTrue();
            verify(completionService).completeSession(any());
        }

        @Test
        @DisplayName("correct guess on first of two countries advances to next country")
        void correctGuessOnFirstCountry_advancesToNext() {
            SessionCountryRow c1 = country(1L, CORRECT_COUNTRY_ID, 1, 3, 0, 0, false);
            SessionCountryRow c2 = country(2L, 55L, 2, 3, 0, 0, false);
            SessionRow session = session("EASY", List.of(c1, c2));
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
            when(countrySearchRepository.findByCode("DE"))
                    .thenReturn(Optional.of(countryDto(CORRECT_COUNTRY_ID, "DE")));
            when(completionService.calculateXp(any(), anyInt(), anyInt(), anyLong(), anyInt(), anyInt()))
                    .thenReturn(100);

            GuessResultDto result = gameplayService.guess(SESSION_ID, "DE", USER_ID);

            assertThat(result.sessionComplete()).isFalse();
            assertThat(result.countryComplete()).isTrue();
            verify(sessionRepository).updateCurrentCountryIdx(SESSION_ID, 1);
            verify(sessionRepository).activateSessionCountry(SESSION_ID, 2);
        }
    }

    @Nested
    @DisplayName("guess - wrong answer")
    class GuessWrong {

        @Test
        @DisplayName("wrong guess reveals next hint automatically")
        void wrongGuess_autoRevealNextHint() {
            SessionRow session = session("EASY", List.of(country(0, 0, false)));
            HintDto autoHint = new HintDto(200L, "Clue text", 1, "geo", "text", "wiki", null);
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
            when(countrySearchRepository.findByCode("FR"))
                    .thenReturn(Optional.of(countryDto(WRONG_COUNTRY_ID, "FR")));
            when(hintParser.getHintAtPosition(any(), eq(1), eq(1))).thenReturn(autoHint);

            GuessResultDto result = gameplayService.guess(SESSION_ID, "FR", USER_ID);

            assertThat(result.correct()).isFalse();
            assertThat(result.nextHint()).isEqualTo(autoHint);
            assertThat(result.hintsRevealed()).isEqualTo(1);
        }

        @Test
        @DisplayName("wrong guess does NOT auto-reveal hint when all hints are already shown")
        void wrongGuess_noHintWhenAllRevealed() {
            // hints_revealed = 3 = maxHints -> canRevealHint = false
            SessionRow session = session("EASY", List.of(country(3, 1, false)));
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
            when(countrySearchRepository.findByCode("FR"))
                    .thenReturn(Optional.of(countryDto(WRONG_COUNTRY_ID, "FR")));

            GuessResultDto result = gameplayService.guess(SESSION_ID, "FR", USER_ID);

            assertThat(result.nextHint()).isNull();
            verify(hintParser, never()).getHintAtPosition(any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("wrong guess on last attempt marks country complete and advances session")
        void wrongGuessLastAttempt_marksCountryCompleteAndAdvances() {
            // attemptsUsed=2, maxAttempts=3 -> this is the 3rd attempt -> no more
            SessionCountryRow c1 = country(1L, CORRECT_COUNTRY_ID, 1, 3, 0, 2, false);
            SessionCountryRow c2 = country(2L, 55L, 2, 3, 0, 0, false);
            SessionRow session = session("EASY", List.of(c1, c2));
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
            when(countrySearchRepository.findByCode("FR"))
                    .thenReturn(Optional.of(countryDto(WRONG_COUNTRY_ID, "FR")));

            GuessResultDto result = gameplayService.guess(SESSION_ID, "FR", USER_ID);

            assertThat(result.countryComplete()).isTrue();
            assertThat(result.correct()).isFalse();
            assertThat(result.xpEarned()).isZero();
            assertThat(result.correctCountry()).isNotNull();
            verify(sessionRepository).updateCurrentCountryIdx(SESSION_ID, 1);
            verify(sessionRepository).activateSessionCountry(SESSION_ID, 2);
        }

        @Test
        @DisplayName("correct country is included in result when country is complete (failed)")
        void failedCountry_revealsCorrectCountry() {
            SessionRow session = session("EASY", List.of(country(0, 2, false)));
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
            when(countrySearchRepository.findByCode("FR"))
                    .thenReturn(Optional.of(countryDto(WRONG_COUNTRY_ID, "FR")));

            GuessResultDto result = gameplayService.guess(SESSION_ID, "FR", USER_ID);

            assertThat(result.correctCountry()).isNotNull();
            assertThat(result.correctCountry().name()).isEqualTo("Germany");
        }
    }

    @Nested
    @DisplayName("guess - validation")
    class GuessValidation {

        @Test
        @DisplayName("throws SESSION_NOT_IN_PROGRESS when session is completed")
        void completedSession_throwsSessionNotInProgress() {
            SessionRow completedSession = new SessionRow(SESSION_ID, USER_ID, null, 10L,
                    LocalDate.of(2025, 3, 13), "EASY", SessionStatus.COMPLETED,
                    0, Instant.now(), Instant.now(), Instant.now(), 0, 3, 3, 0L, 1, null, null, "[]", List.of());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(completedSession));

            assertThatThrownBy(() -> gameplayService.guess(SESSION_ID, "DE", USER_ID))
                    .isInstanceOf(GameException.class)
                    .extracting("errorCode").isEqualTo("SESSION_NOT_IN_PROGRESS");
        }

        @Test
        @DisplayName("throws NO_ATTEMPTS_LEFT when country is already complete (guessed)")
        void alreadyGuessedCountry_throwsNoAttemptsLeft() {
            SessionRow session = session("EASY", List.of(country(0, 0, true)));
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> gameplayService.guess(SESSION_ID, "DE", USER_ID))
                    .isInstanceOf(GameException.class)
                    .extracting("errorCode").isEqualTo("NO_ATTEMPTS_LEFT");
        }

        @Test
        @DisplayName("throws NO_ATTEMPTS_LEFT when all attempts exhausted")
        void maxAttemptsReached_throwsNoAttemptsLeft() {
            // attemptsUsed = maxAttempts -> canGuess = false
            SessionRow session = session("EASY", List.of(country(0, 3, false)));
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> gameplayService.guess(SESSION_ID, "DE", USER_ID))
                    .isInstanceOf(GameException.class)
                    .extracting("errorCode").isEqualTo("NO_ATTEMPTS_LEFT");
        }

        @Test
        @DisplayName("searchCountries returns empty list for query shorter than 2 characters")
        void searchCountries_shortQuery_returnsEmpty() {
            assertThat(gameplayService.searchCountries("A")).isEmpty();
            assertThat(gameplayService.searchCountries("")).isEmpty();
            assertThat(gameplayService.searchCountries(null)).isEmpty();
        }

        @Test
        @DisplayName("throws SESSION_ACCESS_DENIED before resolving countries when principal does not own session")
        void wrongUser_deniesGuessBeforeLookup() {
            SessionRow session = session("EASY", List.of(country(0, 0, false)));
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
            doThrow(GameException.sessionAccessDenied())
                    .when(ignored)
                    .validateAccess(session, 999L);

            assertThatThrownBy(() -> gameplayService.guess(SESSION_ID, "DE", 999L))
                    .isInstanceOf(GameException.class)
                    .extracting("errorCode").isEqualTo("SESSION_ACCESS_DENIED");

            verify(countrySearchRepository, never()).findByCode(any());
            verify(sessionRepository, never()).insertGuessAttempt(anyLong(), anyLong(), anyBoolean(), anyInt(), anyInt());
        }
    }

    @Nested
    @DisplayName("revealHint")
    class RevealHint {

        @Test
        @DisplayName("EASY level: reveals hint without consuming an attempt")
        void easyLevel_revealHintIsFree() {
            SessionCountryRow country = country(1L, CORRECT_COUNTRY_ID, 1, 3, 1, 1, false);
            SessionRow session = session("EASY", List.of(country));
            HintDto hint = new HintDto(101L, "hint", 1, "geo", "text", "wiki", null);
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
            when(hintParser.getHintAtPosition(any(), eq(1), eq(2))).thenReturn(hint);

            RevealResultDto result = gameplayService.revealHint(SESSION_ID, USER_ID);

            assertThat(result.hint()).isEqualTo(hint);
            assertThat(result.hintsRevealed()).isEqualTo(2);
            assertThat(result.attemptsRemaining()).isEqualTo(2);
        }

        @Test
        @DisplayName("MEDIUM level: reveals hint without consuming an attempt")
        void mediumLevel_revealHintIsFree() {
            SessionCountryRow country = country(1L, CORRECT_COUNTRY_ID, 1, 3, 1, 1, false);
            SessionRow session = session("MEDIUM", List.of(country));
            HintDto hint = new HintDto(101L, "hint", 1, "geo", "text", "wiki", null);
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
            when(hintParser.getHintAtPosition(any(), eq(1), eq(2))).thenReturn(hint);

            RevealResultDto result = gameplayService.revealHint(SESSION_ID, USER_ID);

            assertThat(result.attemptsRemaining()).isEqualTo(2);
            verify(sessionRepository).updateSessionCountry(
                    eq(country.id()), eq(2), eq(1), eq(false), eq(null), eq(ACTIVE_TIME_MS), anyInt());
            verify(sessionRepository).updateSessionActivity(SESSION_ID, ACTIVITY_AT);
        }

        @Test
        @DisplayName("MEDIUM: can reveal hint even with only 1 attempt left")
        void mediumLevel_canRevealWithOneAttemptLeft() {
            SessionCountryRow country = country(1L, CORRECT_COUNTRY_ID, 1, 3, 1, 2, false);
            SessionRow session = session("MEDIUM", List.of(country));
            HintDto hint = new HintDto(101L, "hint", 1, "geo", "text", "wiki", null);
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
            when(hintParser.getHintAtPosition(any(), eq(1), eq(2))).thenReturn(hint);

            RevealResultDto result = gameplayService.revealHint(SESSION_ID, USER_ID);

            assertThat(result.attemptsRemaining()).isEqualTo(1);
            assertThat(result.hintsRevealed()).isEqualTo(2);
        }

        @Test
        @DisplayName("throws NO_HINTS_LEFT when all hints already revealed")
        void allHintsRevealed_throwsNoHintsLeft() {
            // hintsRevealed = maxHints = 3
            SessionCountryRow country = country(1L, CORRECT_COUNTRY_ID, 1, 3, 3, 0, false);
            SessionRow session = session("EASY", List.of(country));
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> gameplayService.revealHint(SESSION_ID, USER_ID))
                    .isInstanceOf(GameException.class)
                    .extracting("errorCode").isEqualTo("NO_HINTS_LEFT");
        }

        @Test
        @DisplayName("uses per-country total hints instead of session max hints")
        void usesCountryTotalHints() {
            SessionCountryRow country = country(1L, CORRECT_COUNTRY_ID, 1, 5, 5, 0, false);
            SessionRow session = session("EASY", List.of(country), 6);
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> gameplayService.revealHint(SESSION_ID, USER_ID))
                    .isInstanceOf(GameException.class)
                    .extracting("errorCode").isEqualTo("NO_HINTS_LEFT");
        }

        @Test
        @DisplayName("throws NO_HINTS_LEFT when country is already guessed")
        void alreadyGuessedCountry_cantRevealHint() {
            SessionCountryRow country = country(1L, CORRECT_COUNTRY_ID, 1, 3, 1, 1, true);
            SessionRow session = session("EASY", List.of(country));
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> gameplayService.revealHint(SESSION_ID, USER_ID))
                    .isInstanceOf(GameException.class)
                    .extracting("errorCode").isEqualTo("NO_HINTS_LEFT");
        }

        @Test
        @DisplayName("throws SESSION_ACCESS_DENIED before mutating state when principal does not own session")
        void wrongUser_deniesRevealHint() {
            SessionCountryRow country = country(1L, CORRECT_COUNTRY_ID, 1, 3, 1, 1, false);
            SessionRow session = session("EASY", List.of(country));
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
            doThrow(GameException.sessionAccessDenied())
                    .when(ignored)
                    .validateAccess(session, 999L);

            assertThatThrownBy(() -> gameplayService.revealHint(SESSION_ID, 999L))
                    .isInstanceOf(GameException.class)
                    .extracting("errorCode").isEqualTo("SESSION_ACCESS_DENIED");

            verify(sessionRepository, never())
                    .updateSessionCountry(eq(country.id()), anyInt(), anyInt(), eq(false), isNull(), anyLong(), anyInt());
        }
    }

    private static SessionRow session(String level, List<SessionCountryRow> countries) {
        return session(level, countries, 3);
    }

    private static SessionRow session(String level, List<SessionCountryRow> countries, int maxHintsPerCountry) {
        Instant startedAt = Instant.parse("2025-03-13T10:00:00Z");
        return new SessionRow(
                SESSION_ID, USER_ID, null, 10L, LocalDate.of(2025, 3, 13), level, SessionStatus.IN_PROGRESS, 0,
                startedAt, startedAt, null, 0, maxHintsPerCountry, 3, 0L, 1, null, null, "[]", countries);
    }

    private static SessionCountryRow country(int hintsRevealed, int attemptsUsed, boolean guessed) {
        return country(1L, CORRECT_COUNTRY_ID, 1, 3, hintsRevealed, attemptsUsed, guessed);
    }

    private static SessionCountryRow country(
            long id, long countryId, int order, int totalHints, int hintsRevealed, int attemptsUsed, boolean guessed) {
        return new SessionCountryRow(
                id, countryId, order, totalHints, hintsRevealed, attemptsUsed, guessed, 0L, 0,
                "DE", "Germany", "🇩🇪", "url", "Europe");
    }

    private static CountryDto countryDto(long id, String code) {
        return new CountryDto(id, code, "Germany", "🇩🇪", "url", "Europe");
    }
}
