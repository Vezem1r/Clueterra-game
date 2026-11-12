package io.clueterra.api.game.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.clueterra.api.admin.internal.questions.service.ClueterraQuestionsClient;
import io.clueterra.api.admin.settings.service.SettingsService;
import io.clueterra.api.auth.repository.AuthJdbcRepository;
import io.clueterra.api.auth.repository.UserRow;
import io.clueterra.api.common.enums.SessionStatus;
import io.clueterra.api.common.utils.redis.RedisHelper;
import io.clueterra.api.exception.GameException;
import io.clueterra.api.game.dto.ChallengeMetadata;
import io.clueterra.api.game.repository.ChallengeJdbcRepository;
import io.clueterra.api.game.repository.ChallengeJdbcRepository.ChallengeRow;
import io.clueterra.api.game.repository.ChallengeJdbcRepository.ChallengeSummaryRow;
import io.clueterra.api.game.repository.GameSessionJdbcRepository;
import io.clueterra.api.game.repository.GuestProgressRow;
import io.clueterra.api.game.repository.SessionCountryRow;
import io.clueterra.api.game.repository.SessionRow;
import io.clueterra.api.legal.service.LegalAcceptanceService;
import io.clueterra.api.security.UserPrincipal;

@ExtendWith(MockitoExtension.class)
class GameSessionServiceTest {

    private static final String TERMS_VERSION = "2026-04-21";
    private static final String PRIVACY_VERSION = "2026-04-21";

    @Mock
    private GameSessionJdbcRepository sessionRepository;
    @Mock
    private ChallengeJdbcRepository challengeRepository;
    @Mock
    private ChallengeHintParser hintParser;
    @Mock
    private SettingsService settingsService;
    @Mock
    private GameCompletionService completionService;
    @Mock
    private ClueterraQuestionsClient questionsClient;
    @Mock
    private RedisHelper redisHelper;
    @Mock
    private LegalAcceptanceService legalAcceptanceService;
    @Mock
    private AuthJdbcRepository authRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2025-03-13T10:00:00Z"), ZoneOffset.UTC);

    private GameSessionService service;

    @Nested
    @DisplayName("validateAccess - session owner")
    class ValidateAccess {

        @Test
        @DisplayName("passes when shadow guest principal owns the session")
        void shadowGuestOwner_passes() {
            SessionRow guestSession = shadowGuestSession();
            assertThatCode(() -> service.validateAccess(guestSession, 100L))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("passes when registered principal owns the session")
        void registeredOwner_passes() {
            SessionRow userSession = userSession();
            assertThatCode(() -> service.validateAccess(userSession, 42L))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("throws SESSION_ACCESS_DENIED when userId does not match")
        void differentUserId_denies() {
            SessionRow userSession = userSession();
            assertThatThrownBy(() -> service.validateAccess(userSession, 999L))
                    .isInstanceOf(GameException.class)
                    .extracting("errorCode").isEqualTo("SESSION_ACCESS_DENIED");
        }

        @Test
        @DisplayName("throws SESSION_ACCESS_DENIED when principal is absent")
        void nullUserId_denies() {
            SessionRow userSession = userSession();
            assertThatThrownBy(() -> service.validateAccess(userSession, null))
                    .isInstanceOf(GameException.class)
                    .extracting("errorCode").isEqualTo("SESSION_ACCESS_DENIED");
        }
    }

    @Nested
    @DisplayName("startGuestSession - maxHints clamping")
    class MaxHintsClamping {

        @ParameterizedTest(name = "challengeHints={0} -> expects {0}")
        @CsvSource({
                "2",
                "10",
                "0",
                "3"
        })
        @DisplayName("uses challenge hint count directly")
        void determinesMaxHintsCorrectly(int challengeHints) {
            when(settingsService.getMaxAttempts()).thenReturn(3);
            when(hintParser.countHintsPerCountry(any())).thenReturn(challengeHints);
            stubChallengeAndSession();
            service.startGuestSession(TERMS_VERSION, PRIVACY_VERSION, null);
            verify(legalAcceptanceService).validateSubmittedVersions(TERMS_VERSION, PRIVACY_VERSION);
            verify(sessionRepository).insertSession(
                    eq(100L), isNull(), anyLong(), anyString(), eq(challengeHints), eq(3),
                    eq(TERMS_VERSION), eq(PRIVACY_VERSION), any(Instant.class));
        }

        private void stubChallengeAndSession() {
            ChallengeRow challenge = new ChallengeRow(
                    10L, LocalDate.now(clock), "EASY", "MEDIUM", 1, 3,
                    "[{\"countryId\":42,\"order\":1}]",
                    "[{\"countryOrder\":1,\"hintOrder\":1}]");

            when(authRepository.insertGuestUser(TERMS_VERSION, PRIVACY_VERSION)).thenReturn(100L);
            when(sessionRepository.existsParticipation(eq(100L), any())).thenReturn(false);
            when(challengeRepository.findByDateAndLevel(any(), eq("EASY"))).thenReturn(Optional.of(challenge));
            when(redisHelper.read(anyString(), eq(ChallengeRow.class))).thenReturn(Optional.empty());
            when(sessionRepository.insertSession(any(), any(), anyLong(), anyString(), anyInt(), anyInt(), any(), any(), any()))
                    .thenReturn(99L);
            when(sessionRepository.insertParticipation(eq(100L), any(), anyString(), eq(99L))).thenReturn(1);
            when(hintParser.parseSessionCountries(any(), any())).thenReturn(List.of());
            SessionRow sessionRow = shadowGuestSession();
            when(sessionRepository.findById(99L)).thenReturn(Optional.of(sessionRow));
            when(authRepository.findById(100L)).thenReturn(Optional.of(shadowUser()));
        }
    }

    @Nested
    @DisplayName("startGuestSession - reuse active guest session")
    class GuestSessionReuse {

        @Test
        @DisplayName("reuses in-progress guest session for today when shadow user already participated")
        void reusesExistingGuestSession() {
            SessionRow existing = new SessionRow(
                    1L, 100L, null, 10L, LocalDate.of(2025, 3, 13), "EASY", SessionStatus.IN_PROGRESS, 0,
                    Instant.now(), Instant.now(), null, 0, 3, 3, 0L, 1,
                    TERMS_VERSION, PRIVACY_VERSION, "[]", List.of());
            ChallengeRow challenge = new ChallengeRow(
                    10L, LocalDate.now(clock), "EASY", "MEDIUM", 1, 3,
                    "[{\"countryId\":42,\"order\":1}]",
                    "[{\"countryOrder\":1,\"hintOrder\":1}]");
            UserRow shadowUser = new UserRow(100L, null, null, null, "USER", true, null, false, null, null, null, null, true, "secret-token");

            when(challengeRepository.findByDateAndLevel(any(), eq("EASY"))).thenReturn(Optional.of(challenge));
            when(redisHelper.read(anyString(), eq(ChallengeRow.class))).thenReturn(Optional.empty());
            when(authRepository.findByGuestToken("secret-token")).thenReturn(Optional.of(shadowUser));
            when(sessionRepository.existsParticipation(100L, LocalDate.now(clock))).thenReturn(true);
            when(sessionRepository.findTodaySessionForUser(100L)).thenReturn(Optional.of(existing));
            when(authRepository.findById(100L)).thenReturn(Optional.of(shadowUser));

            var dto = service.startGuestSession(TERMS_VERSION, PRIVACY_VERSION, "secret-token");

            assertThat(dto.id()).isEqualTo(1L);
            assertThat(dto.guest()).isTrue();
            verify(authRepository).refreshGuestTokenExpiry(100L);
            verify(sessionRepository, never()).insertSession(any(), any(), anyLong(), anyString(), anyInt(), anyInt(), any(), any(), any());
        }

        @Test
        @DisplayName("creates a new shadow guest when submitted guest cookie is stale")
        void staleGuestCookie_createsNewShadowGuest() {
            when(settingsService.getMaxAttempts()).thenReturn(3);
            when(hintParser.countHintsPerCountry(any())).thenReturn(3);
            when(authRepository.findByGuestToken("stale-token")).thenReturn(Optional.empty());
            stubNewGuestSession();

            var dto = service.startGuestSession(TERMS_VERSION, PRIVACY_VERSION, "stale-token");

            assertThat(dto.id()).isEqualTo(99L);
            assertThat(dto.guest()).isTrue();
            verify(authRepository).insertGuestUser(TERMS_VERSION, PRIVACY_VERSION);
            verify(sessionRepository).insertSession(
                    eq(100L), isNull(), anyLong(), eq("EASY"), eq(3), eq(3),
                    eq(TERMS_VERSION), eq(PRIVACY_VERSION), any(Instant.class));
        }

        @Test
        @DisplayName("refreshes existing guest expiry when starting a new-day session")
        void existingGuestNewSession_refreshesExpiry() {
            UserRow shadowUser = new UserRow(100L, null, null, null, "USER", true, null, false, null, null, null, null, true, "secret-token");

            when(settingsService.getMaxAttempts()).thenReturn(3);
            when(hintParser.countHintsPerCountry(any())).thenReturn(3);
            when(authRepository.findByGuestToken("secret-token")).thenReturn(Optional.of(shadowUser));
            stubExistingGuestNewSession();

            var dto = service.startGuestSession(TERMS_VERSION, PRIVACY_VERSION, "secret-token");

            assertThat(dto.id()).isEqualTo(99L);
            assertThat(dto.guest()).isTrue();
            verify(authRepository).refreshGuestTokenExpiry(100L);
            verify(authRepository, never()).insertGuestUser(anyString(), anyString());
            verify(sessionRepository).insertSession(
                    eq(100L), isNull(), anyLong(), eq("EASY"), eq(3), eq(3),
                    eq(TERMS_VERSION), eq(PRIVACY_VERSION), any(Instant.class));
        }

        private void stubNewGuestSession() {
            ChallengeRow challenge = new ChallengeRow(
                    10L, LocalDate.now(clock), "EASY", "MEDIUM", 1, 3,
                    "[{\"countryId\":42,\"order\":1}]",
                    "[{\"countryOrder\":1,\"hintOrder\":1}]");

            when(authRepository.insertGuestUser(TERMS_VERSION, PRIVACY_VERSION)).thenReturn(100L);
            when(sessionRepository.existsParticipation(100L, LocalDate.now(clock))).thenReturn(false);
            when(challengeRepository.findByDateAndLevel(any(), eq("EASY"))).thenReturn(Optional.of(challenge));
            when(redisHelper.read(anyString(), eq(ChallengeRow.class))).thenReturn(Optional.empty());
            when(sessionRepository.insertSession(any(), any(), anyLong(), anyString(), anyInt(), anyInt(), any(), any(), any()))
                    .thenReturn(99L);
            when(sessionRepository.insertParticipation(eq(100L), any(), anyString(), eq(99L))).thenReturn(1);
            when(hintParser.parseSessionCountries(any(), any())).thenReturn(List.of());
            when(sessionRepository.findById(99L)).thenReturn(Optional.of(shadowGuestSession(99L)));
            when(authRepository.findById(100L)).thenReturn(Optional.of(shadowUser()));
        }

        private void stubExistingGuestNewSession() {
            ChallengeRow challenge = new ChallengeRow(
                    10L, LocalDate.now(clock), "EASY", "MEDIUM", 1, 3,
                    "[{\"countryId\":42,\"order\":1}]",
                    "[{\"countryOrder\":1,\"hintOrder\":1}]");

            when(sessionRepository.existsParticipation(100L, LocalDate.now(clock))).thenReturn(false);
            when(challengeRepository.findByDateAndLevel(any(), eq("EASY"))).thenReturn(Optional.of(challenge));
            when(redisHelper.read(anyString(), eq(ChallengeRow.class))).thenReturn(Optional.empty());
            when(sessionRepository.insertSession(any(), any(), anyLong(), anyString(), anyInt(), anyInt(), any(), any(), any()))
                    .thenReturn(99L);
            when(sessionRepository.insertParticipation(eq(100L), any(), anyString(), eq(99L))).thenReturn(1);
            when(hintParser.parseSessionCountries(any(), any())).thenReturn(List.of());
            when(sessionRepository.findById(99L)).thenReturn(Optional.of(shadowGuestSession(99L)));
            when(authRepository.findById(100L)).thenReturn(Optional.of(shadowUser()));
        }
    }

    @Nested
    @DisplayName("getTodaysChallenges")
    class GetTodaysChallenges {

        @Test
        @DisplayName("uses estimated average XP when there are no completed runs yet")
        void usesEstimatedAverageXpFallback() {
            ChallengeSummaryRow challenge = new ChallengeSummaryRow(
                    10L, LocalDate.now(clock), "MEDIUM", "HARD", 6, 36,
                    "[{\"countryId\":1,\"order\":1}]",
                    "[{\"countryOrder\":1,\"hintOrder\":1}]",
                    4,
                    null);

            when(challengeRepository.findActiveSummariesByDate(LocalDate.now(clock))).thenReturn(List.of(challenge));
            when(hintParser.extractMetadata(challenge.hintsJson())).thenReturn(new ChallengeMetadata(12, 8));
            when(settingsService.getMaxAttempts()).thenReturn(3);
            when(settingsService.getTimeBonusThreshold()).thenReturn(180);
            when(completionService.calculateXp(anyString(), anyInt(), anyInt(), anyLong(), anyInt(), anyInt())).thenReturn(42);

            var challenges = service.getTodaysChallenges(null);

            assertThat(challenges).singleElement().satisfies(dto -> {
                assertThat(dto.avgXp()).isEqualTo(252);
                assertThat(dto.dataSourceCount()).isEqualTo(12);
                assertThat(dto.categoryCount()).isEqualTo(8);
                assertThat(dto.continentCount()).isEqualTo(4);
            });
        }
    }

    @Nested
    @DisplayName("toSessionDto")
    class ToSessionDto {

        @Test
        @DisplayName("uses summed country XP while session is in progress")
        void inProgress_usesSummedCountryXp() {
            when(hintParser.getHintsForCountry(anyString(), anyInt(), anyInt())).thenReturn(List.of());
            Instant startedAt = Instant.parse("2025-03-13T10:00:00Z");
            SessionRow session = new SessionRow(
                    1L, 42L, null, 10L, LocalDate.of(2025, 3, 13), "EASY", SessionStatus.IN_PROGRESS, 0,
                    startedAt, startedAt.plusSeconds(20), null, 0, 3, 3, 0L, 1, null, null, "[]",
                    List.of(
                            new SessionCountryRow(1L, 42L, 1, 3, 1, 1, true, 5_000L, 80,
                                    "DE", "Germany", "🇩🇪", "url", "Europe"),
                            new SessionCountryRow(2L, 43L, 2, 3, 0, 0, false, 15_000L, 20,
                                    "FR", "France", "🇫🇷", "url", "Europe")));

            var dto = service.toSessionDto(session);

            assertThat(dto.totalXpEarned()).isEqualTo(100);
            assertThat(dto.activeTimeMs()).isEqualTo(20_000L);
            assertThat(dto.guest()).isFalse();
        }

        @Test
        @DisplayName("marks shadow guest sessions explicitly")
        void shadowGuest_setsGuestFlag() {
            when(authRepository.findById(100L)).thenReturn(Optional.of(shadowUser()));

            var dto = service.toSessionDto(shadowGuestSession());

            assertThat(dto.guest()).isTrue();
        }
    }

    @Nested
    @DisplayName("getTodaySessionSummary")
    class GetTodaySessionSummary {

        @Test
        @DisplayName("returns empty when there is no principal")
        void noPrincipal_returnsEmpty() {
            assertThat(service.getTodaySessionSummary(null)).isEmpty();
        }

        @Test
        @DisplayName("loads summary by principal user id")
        void principal_loadsSummary() {
            GameSessionJdbcRepository.SessionSummaryRow row =
                    new GameSessionJdbcRepository.SessionSummaryRow(
                            1L, "EASY", SessionStatus.IN_PROGRESS, 0,
                            3, 1, 4, 2, Instant.now(), 12_000L, null, 50);
            when(sessionRepository.findTodaySummaryForUser(100L, LocalDate.now(clock)))
                    .thenReturn(Optional.of(row));

            var summary = service.getTodaySessionSummary(100L);

            assertThat(summary).isPresent();
            assertThat(summary.get().id()).isEqualTo(1L);
            assertThat(summary.get().totalXpEarned()).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("getGuestProgress")
    class GetGuestProgress {

        @Test
        @DisplayName("returns empty DTO for registered users")
        void registeredUserGetsNoGuestProgress() {
            UserPrincipal principal = new UserPrincipal(
                    42L, "user@example.com", "hash", "user", "USER", true,
                    TERMS_VERSION, PRIVACY_VERSION, false);

            var dto = service.getGuestProgress(principal);

            assertThat(dto.hasGuest()).isFalse();
            assertThat(dto.playCount()).isZero();
            verify(sessionRepository, never()).findGuestProgressForUser(anyLong());
        }

        @Test
        @DisplayName("maps guest progress row for guest users")
        void guestUserGetsProgress() {
            Instant expiresAt = Instant.parse("2025-04-12T10:00:00Z");
            UserPrincipal principal = new UserPrincipal(
                    100L, null, null, null, "USER", true,
                    TERMS_VERSION, PRIVACY_VERSION, true);
            when(sessionRepository.findGuestProgressForUser(100L)).thenReturn(Optional.of(new GuestProgressRow(
                    3, 2, 450L, 4, 100, 4, 5, 2, expiresAt)));

            var dto = service.getGuestProgress(principal);

            assertThat(dto.hasGuest()).isTrue();
            assertThat(dto.playCount()).isEqualTo(3);
            assertThat(dto.budgetPlayCount()).isEqualTo(2);
            assertThat(dto.totalXp()).isEqualTo(450L);
            assertThat(dto.currentLevel()).isEqualTo(4);
            assertThat(dto.levelDivisor()).isEqualTo(100);
            assertThat(dto.currentStreak()).isEqualTo(4);
            assertThat(dto.bestStreak()).isEqualTo(5);
            assertThat(dto.badgesEarned()).isEqualTo(2);
            assertThat(dto.expiresAt()).isEqualTo(expiresAt);
        }
    }

    @Nested
    @DisplayName("getGuestState")
    class GetGuestState {

        @Test
        @DisplayName("returns none when principal is absent")
        void noPrincipal_returnsNone() {
            var dto = service.getGuestState(null);

            assertThat(dto.hasGuest()).isFalse();
            assertThat(dto.needsLegalAcceptance()).isFalse();
            assertThat(dto.hasTodaySession()).isFalse();
            verify(sessionRepository, never()).findTodaySessionForUser(anyLong());
            verify(legalAcceptanceService, never()).requiresAcceptance(any());
        }

        @Test
        @DisplayName("returns none for registered users")
        void registeredUser_returnsNone() {
            UserPrincipal principal = new UserPrincipal(
                    42L, "user@example.com", "hash", "user", "USER", true,
                    TERMS_VERSION, PRIVACY_VERSION, false);

            var dto = service.getGuestState(principal);

            assertThat(dto.hasGuest()).isFalse();
            assertThat(dto.needsLegalAcceptance()).isFalse();
            assertThat(dto.hasTodaySession()).isFalse();
            verify(sessionRepository, never()).findTodaySessionForUser(anyLong());
            verify(legalAcceptanceService, never()).requiresAcceptance(any());
        }

        @Test
        @DisplayName("returns current guest state without a today session")
        void currentGuestWithoutSession_returnsNoSession() {
            UserPrincipal principal = new UserPrincipal(
                    100L, null, null, null, "USER", true,
                    TERMS_VERSION, PRIVACY_VERSION, true);
            when(legalAcceptanceService.requiresAcceptance(principal)).thenReturn(false);
            when(sessionRepository.findTodaySessionForUser(100L)).thenReturn(Optional.empty());

            var dto = service.getGuestState(principal);

            assertThat(dto.hasGuest()).isTrue();
            assertThat(dto.needsLegalAcceptance()).isFalse();
            assertThat(dto.hasTodaySession()).isFalse();
            assertThat(dto.todaySessionId()).isNull();
            assertThat(dto.todaySessionStatus()).isNull();
        }

        @Test
        @DisplayName("returns legal reacceptance state with today's guest session")
        void outdatedGuestWithTodaySession_returnsReacceptanceState() {
            UserPrincipal principal = new UserPrincipal(
                    100L, null, null, null, "USER", true,
                    "old-terms", PRIVACY_VERSION, true);
            when(legalAcceptanceService.requiresAcceptance(principal)).thenReturn(true);
            when(sessionRepository.findTodaySessionForUser(100L))
                    .thenReturn(Optional.of(shadowGuestSession(99L)));

            var dto = service.getGuestState(principal);

            assertThat(dto.hasGuest()).isTrue();
            assertThat(dto.needsLegalAcceptance()).isTrue();
            assertThat(dto.hasTodaySession()).isTrue();
            assertThat(dto.todaySessionId()).isEqualTo(99L);
            assertThat(dto.todaySessionStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        }
    }

    private static SessionRow shadowGuestSession() {
        return shadowGuestSession(1L);
    }

    private static SessionRow shadowGuestSession(long sessionId) {
        return new SessionRow(
                sessionId, 100L, null, 10L, LocalDate.of(2025, 3, 13), "EASY", SessionStatus.IN_PROGRESS, 0,
                Instant.now(), Instant.now(), null, 0, 3, 3, 0L, 1,
                TERMS_VERSION, PRIVACY_VERSION, "[]", List.of());
    }

    private static SessionRow userSession() {
        return new SessionRow(
                1L, 42L, null, 10L, LocalDate.of(2025, 3, 13), "EASY", SessionStatus.IN_PROGRESS, 0,
                Instant.now(), Instant.now(), null, 0, 3, 3, 0L, 1, null, null, "[]", List.of());
    }

    private static UserRow shadowUser() {
        return new UserRow(
                100L, null, null, null, "USER", true,
                null, false, null, null, TERMS_VERSION, PRIVACY_VERSION, true, "secret-token");
    }

    @BeforeEach
    void injectClock() {
        service = new GameSessionService(
                sessionRepository,
                challengeRepository,
                hintParser,
                settingsService,
                completionService,
                questionsClient,
                redisHelper,
                clock,
                legalAcceptanceService,
                authRepository);
        lenient().when(authRepository.findById(anyLong())).thenReturn(Optional.empty());
    }
}
