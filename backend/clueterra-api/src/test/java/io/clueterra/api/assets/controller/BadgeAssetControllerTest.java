package io.clueterra.api.assets.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.clueterra.api.assets.service.BadgeAssetPayload;
import io.clueterra.api.assets.service.BadgeAssetService;
import io.clueterra.api.config.OAuthProperties;
import io.clueterra.api.filter.RateLimitingFilter;
import io.clueterra.api.security.GuestTokenFilter;
import io.clueterra.api.security.JwtAuthenticationFilter;
import io.clueterra.api.security.LegalAcceptanceFilter;

@WebMvcTest(BadgeAssetController.class)
@AutoConfigureMockMvc(addFilters = false)
class BadgeAssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BadgeAssetService badgeAssetService;
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
    void returnsVersionedImmutablePng() throws Exception {
        when(badgeAssetService.fetch(7L, "0123456789abcdef"))
                .thenReturn(new BadgeAssetPayload(new byte[]{1, 2, 3}, "image/png"));

        mockMvc.perform(get("/api/assets/badges/7/0123456789abcdef.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}))
                .andExpect(header().string("Cache-Control", containsString("max-age=2592000")))
                .andExpect(header().string("Cache-Control", containsString("immutable")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void rejectsMalformedVersionWithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/assets/badges/7/not-a-version.png"))
                .andExpect(status().isBadRequest());
    }
}
