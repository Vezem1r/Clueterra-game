package io.clueterra.api.profile.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import io.clueterra.api.config.OAuthProperties;
import io.clueterra.api.filter.RateLimitingFilter;
import io.clueterra.api.profile.dto.ProfileBadgePageResponse;
import io.clueterra.api.profile.service.ProfileService;
import io.clueterra.api.security.GuestTokenFilter;
import io.clueterra.api.security.JwtAuthenticationFilter;
import io.clueterra.api.security.LegalAcceptanceFilter;
import io.clueterra.api.security.UserPrincipal;

@WebMvcTest(ProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileService profileService;
    @MockitoBean
    private OAuthProperties oauthProperties;
    @MockitoBean
    private RateLimitingFilter rateLimitingFilter;
    @MockitoBean
    private GuestTokenFilter guestTokenFilter;
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean
    private LegalAcceptanceFilter legalAcceptanceFilter;

    @Test
    void rejectsNegativePublicBadgePage() throws Exception {
        mockMvc.perform(get("/api/profile/player/badges?page=-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(profileService);
    }

    @Test
    void rejectsZeroPublicBadgePageSize() throws Exception {
        mockMvc.perform(get("/api/profile/player/badges?size=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(profileService);
    }

    @Test
    void rejectsOversizedPublicBadgePage() throws Exception {
        mockMvc.perform(get("/api/profile/player/badges?size=1000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(profileService);
    }

    @Test
    void acceptsMaximumPublicBadgePageSize() throws Exception {
        ProfileBadgePageResponse response = new ProfileBadgePageResponse(
                List.of(), 0, 0, 2, 50, 0, 0, 0);
        when(profileService.getPublicBadgesPaginated("player", "en", 2, 50))
                .thenReturn(response);

        mockMvc.perform(get("/api/profile/player/badges?page=2&size=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50));

        verify(profileService).getPublicBadgesPaginated("player", "en", 2, 50);
    }

    @Test
    void guestBadgeRouteUsesPrivateGuestIdentityInsteadOfPublicUsername() throws Exception {
        UserPrincipal guest = new UserPrincipal(
                42L, null, null, null, "USER", true, null, null, true);
        ProfileBadgePageResponse response = new ProfileBadgePageResponse(
                List.of(), 0, 0, 0, 9, 0, 0, 0);
        when(profileService.getBadgesPaginated(42L, "ru", "earned", 0, 9))
                .thenReturn(response);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(guest, null, guest.getAuthorities()));
        try {
            mockMvc.perform(get("/api/profile/guest/badges")
                            .header("Accept-Language", "ru-RU"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.earnedCount").value(0));
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(profileService).getBadgesPaginated(42L, "ru", "earned", 0, 9);
    }

    @Test
    void guestProfileRouteUsesPrivateGuestIdentity() throws Exception {
        UserPrincipal guest = new UserPrincipal(
                42L, null, null, null, "USER", true, null, null, true);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(guest, null, guest.getAuthorities()));
        try {
            mockMvc.perform(get("/api/profile/guest"))
                    .andExpect(status().isOk());
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(profileService).getOwnProfile(42L);
    }
}
