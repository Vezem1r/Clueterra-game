package io.clueterra.api.config;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import io.clueterra.api.admin.console.controller.ConsoleLogController;
import io.clueterra.api.admin.console.dto.ConsoleStateDto;
import io.clueterra.api.admin.console.service.ConsoleLogService;
import io.clueterra.api.admin.indicators.controller.AdminIndicatorConfigController;
import io.clueterra.api.admin.indicators.service.IndicatorConfigAdminService;
import io.clueterra.api.assets.controller.QuestionImageAssetController;
import io.clueterra.api.assets.service.QuestionImageAssetPayload;
import io.clueterra.api.assets.service.QuestionImageAssetService;
import io.clueterra.api.auth.repository.AuthJdbcRepository;
import io.clueterra.api.auth.service.JwtService;
import io.clueterra.api.filter.RateLimitingFilter;
import io.clueterra.api.legal.service.LegalAcceptanceService;
import io.clueterra.api.leaderboard.controller.LeaderboardAvatarController;
import io.clueterra.api.leaderboard.controller.LeaderboardController;
import io.clueterra.api.leaderboard.dto.LeaderboardPageResponse;
import io.clueterra.api.leaderboard.service.LeaderboardAvatarThumbnailService;
import io.clueterra.api.leaderboard.service.LeaderboardService;
import io.clueterra.api.profile.controller.ProfileController;
import io.clueterra.api.profile.dto.ProfileDto;
import io.clueterra.api.profile.dto.ProfileBadgePageResponse;
import io.clueterra.api.profile.dto.BudgetProfileStatsDto;
import io.clueterra.api.profile.service.ProfileService;
import io.clueterra.api.reports.controller.HintReportController;
import io.clueterra.api.reports.service.HintReportService;
import io.clueterra.api.security.ClientIpResolver;
import io.clueterra.api.security.GuestTokenFilter;
import io.clueterra.api.security.JwtAuthenticationFilter;
import io.clueterra.api.security.UserPrincipal;

@WebMvcTest(controllers = {
        AdminIndicatorConfigController.class,
        ConsoleLogController.class,
        ProfileController.class,
        LeaderboardAvatarController.class,
        LeaderboardController.class,
        HintReportController.class,
        QuestionImageAssetController.class
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GuestTokenFilter.class})
@TestPropertySource(properties = {
        "clueterra.cors.allowed-origins=http://localhost:3000"
})
class SecurityConfigWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IndicatorConfigAdminService indicatorConfigAdminService;
    @MockitoBean
    private ConsoleLogService consoleLogService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private RateLimitingFilter rateLimitingFilter;
    @MockitoBean
    private LegalAcceptanceService legalAcceptanceService;
    @MockitoBean
    private ProfileService profileService;
    @MockitoBean
    private LeaderboardAvatarThumbnailService leaderboardAvatarThumbnailService;
    @MockitoBean
    private LeaderboardService leaderboardService;
    @MockitoBean
    private HintReportService hintReportService;
    @MockitoBean
    private ClientIpResolver clientIpResolver;
    @MockitoBean
    private AuthJdbcRepository authJdbcRepository;
    @MockitoBean
    private QuestionImageAssetService questionImageAssetService;

    @BeforeEach
    void setUp() throws Exception {
        when(consoleLogService.getState(any())).thenReturn(new ConsoleStateDto(null, List.of()));
        when(indicatorConfigAdminService.getCategories("regular")).thenReturn(List.of("geo"));
        when(profileService.getPublicProfile("player")).thenReturn(new ProfileDto(
                "player",
                null,
                "Player",
                null,
                10L,
                1,
                1,
                1,
                0,
                100.0,
                1,
                1,
                1,
                "easy",
                0L,
                10L,
                10,
                0.0,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new BudgetProfileStatsDto(0, 0, BigDecimal.ZERO)));
        when(clientIpResolver.isDirectLoopback(any())).thenAnswer(invocation -> {
            jakarta.servlet.http.HttpServletRequest request = invocation.getArgument(0);
            return "127.0.0.1".equals(request.getRemoteAddr())
                    && request.getHeader("X-Forwarded-For") == null
                    && request.getHeader("CF-Connecting-IP") == null;
        });
        doAnswer(invocation -> {
            invocation.<jakarta.servlet.FilterChain>getArgument(2)
                    .doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(rateLimitingFilter).doFilter(any(), any(), any());
    }

    @Test
    @DisplayName("anonymous users cannot open admin console routes")
    void anonymousUser_cannotOpenAdminConsole() throws Exception {
        mockMvc.perform(get("/api/admin/console/state"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("public profile remains reachable anonymously")
    void anonymousUser_canOpenPublicProfile() throws Exception {
        mockMvc.perform(get("/api/profile/player"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("player"));
    }

    @Test
    @DisplayName("anonymous users cannot open private profile badge routes")
    void anonymousUser_cannotOpenPrivateProfileBadgeRoutes() throws Exception {
        mockMvc.perform(get("/api/profile/badges"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyCurrentGuestCanOpenGuestBadgeRoute() throws Exception {
        UserPrincipal guest = new UserPrincipal(42L, null, null, null, "USER", true,
                null, null, true);
        UserPrincipal registered = new UserPrincipal(43L, "player@example.com", "hash", "player", "USER", true);
        when(profileService.getBadgesPaginated(42L, "en", "earned", 0, 9))
                .thenReturn(new ProfileBadgePageResponse(List.of(), 0, 0, 0, 9, 0, 0, 0));

        mockMvc.perform(get("/api/profile/guest/badges"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/profile/guest/badges").with(user(registered)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/profile/guest/badges").with(user(guest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.earnedCount").value(0));
    }

    @Test
    void onlyCurrentGuestCanOpenPrivateGuestProfile() throws Exception {
        UserPrincipal guest = new UserPrincipal(42L, null, null, null, "USER", true,
                null, null, true);
        UserPrincipal registered = new UserPrincipal(43L, "player@example.com", "hash", "player", "USER", true);

        mockMvc.perform(get("/api/profile/guest"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/profile/guest").with(user(registered)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/profile/guest").with(user(guest)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("anonymous users cannot acknowledge streak freeze notices")
    void anonymousUser_cannotAcknowledgeStreakFreezeNotice() throws Exception {
        mockMvc.perform(post("/api/profile/streak-freeze-notice/acknowledge"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("anonymous users cannot mutate avatars through profile routes")
    void anonymousUser_cannotDeleteAvatar() throws Exception {
        mockMvc.perform(delete("/api/profile/avatar"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("anonymous users cannot submit hint reports")
    void anonymousUser_cannotSubmitHintReports() throws Exception {
        mockMvc.perform(post("/api/hints/report")
                        .contentType("application/json")
                        .content("{\"hintId\":1,\"reportType\":\"OTHER\",\"description\":\"probe\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("cookie-bearing mutations from a foreign origin are rejected before authentication")
    void foreignOriginMutation_isRejected() throws Exception {
        mockMvc.perform(post("/api/hints/report")
                        .header("Origin", "https://evil.example")
                        .header("Cookie", "clueterra_access=token")
                        .with(user(new UserPrincipal(43L, "player@example.com", "hash", "player", "USER", true)))
                        .contentType("application/json")
                        .content("{\"hintId\":1,\"reportType\":\"OTHER\",\"description\":\"probe\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("cookie-bearing mutations without Origin or Referer are rejected")
    void sourcelessCookieMutation_isRejected() throws Exception {
        mockMvc.perform(delete("/api/profile/avatar")
                        .header("Cookie", "clueterra_access=token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("mutations from the configured origin reach the normal security rules")
    void trustedOriginMutation_reachesAuthorization() throws Exception {
        mockMvc.perform(delete("/api/profile/avatar")
                        .header("Origin", "http://localhost:3000")
                        .header("Cookie", "clueterra_access=token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("statistic images are not public")
    void statisticImage_requiresAdmin() throws Exception {
        mockMvc.perform(get("/api/assets/statistics/84"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/assets/statistics/84")
                        .with(user("player@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admins load statistic images with private caching")
    void statisticImage_isPrivateForAdmins() throws Exception {
        when(questionImageAssetService.fetchStatistic(84L))
                .thenReturn(new QuestionImageAssetPayload(new byte[] {1}, "image/jpeg"));

        mockMvc.perform(get("/api/assets/statistics/84")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Cache-Control"))
                        .contains("private")
                        .doesNotContain("public"));
    }

    @Test
    @DisplayName("anonymous users can load cacheable leaderboard avatar thumbnails")
    void anonymousUser_canLoadLeaderboardAvatarThumbnail() throws Exception {
        String sourceFile = "0c1475b3-41cb-4eec-ae28-65f466530a23.png";
        when(leaderboardAvatarThumbnailService.create(42, sourceFile))
                .thenReturn(Optional.of(new byte[] {1, 2, 3}));

        mockMvc.perform(get("/api/assets/avatars/42/" + sourceFile + "/128.jpg"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).isEqualTo("image/jpeg"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Cache-Control"))
                        .contains("public", "max-age=31536000", "immutable"));
    }

    @Test
    @DisplayName("missing avatar thumbnails use the standard API error response")
    void missingLeaderboardAvatarThumbnail_returnsApiError() throws Exception {
        mockMvc.perform(get("/api/assets/avatars/42/missing.png/128.jpg"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("public leaderboard can use browser caching without private data")
    void anonymousUser_canCacheLeaderboardBriefly() throws Exception {
        when(leaderboardService.getLeaderboard(0, 20))
                .thenReturn(new LeaderboardPageResponse(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Cache-Control"))
                        .contains("public", "max-age=30")
                        .doesNotContain("no-store"));
    }

    @Test
    @DisplayName("non-admin users are forbidden from indicator admin routes")
    void nonAdminUser_cannotOpenIndicatorAdminRoutes() throws Exception {
        mockMvc.perform(get("/api/admin/indicators/categories")
                        .with(user("player@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admins can open indicator admin routes")
    void adminUser_canOpenIndicatorAdminRoutes() throws Exception {
        mockMvc.perform(get("/api/admin/indicators/categories")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("geo"));
    }

    private static RequestPostProcessor remoteAddr(String ip) {
        return request -> {
            ((MockHttpServletRequest) request).setRemoteAddr(ip);
            assertThat(request.getRemoteAddr()).isEqualTo(ip);
            return request;
        };
    }
}
