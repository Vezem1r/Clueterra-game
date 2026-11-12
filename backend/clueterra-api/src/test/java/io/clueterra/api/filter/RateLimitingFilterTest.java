package io.clueterra.api.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.clueterra.api.config.RateLimitingProperties;
import io.clueterra.api.security.ClientIpResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitingFilterTest {

    @Test
    @DisplayName("guest session creation uses a stricter dedicated bucket than generic game requests")
    void guestSessionCreationUsesDedicatedBucket() throws Exception {
        RateLimitingFilter filter = newFilter(10, 10, 2);

        MockHttpServletResponse first = perform(filter, "POST", "/api/game/sessions/guest");
        MockHttpServletResponse second = perform(filter, "POST", "/api/game/sessions/guest");
        MockHttpServletResponse third = perform(filter, "POST", "/api/game/sessions/guest");
        MockHttpServletResponse genericGame = perform(filter, "GET", "/api/game/today/easy");

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(second.getStatus()).isEqualTo(200);
        assertThat(third.getStatus()).isEqualTo(429);
        assertThat(third.getContentAsString()).contains("Rate limit exceeded");
        assertThat(genericGame.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("login and register use dedicated auth buckets instead of the shared auth bucket")
    void loginAndRegisterUseDedicatedBuckets() throws Exception {
        RateLimitingFilter filter = newFilter(1, 1, 5);

        MockHttpServletResponse loginFirst = perform(filter, "POST", "/api/auth/login");
        MockHttpServletResponse loginSecond = perform(filter, "POST", "/api/auth/login");
        MockHttpServletResponse registerFirst = perform(filter, "POST", "/api/auth/register");
        MockHttpServletResponse registerSecond = perform(filter, "POST", "/api/auth/register");
        MockHttpServletResponse refreshFirst = perform(filter, "POST", "/api/auth/refresh");
        MockHttpServletResponse refreshSecond = perform(filter, "POST", "/api/auth/refresh");

        assertThat(loginFirst.getStatus()).isEqualTo(200);
        assertThat(loginSecond.getStatus()).isEqualTo(429);
        assertThat(registerFirst.getStatus()).isEqualTo(200);
        assertThat(registerSecond.getStatus()).isEqualTo(429);
        assertThat(refreshFirst.getStatus()).isEqualTo(200);
        assertThat(refreshSecond.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("avatar upload uses a dedicated 1 per minute bucket and delete avatar stays unaffected")
    void avatarUploadUsesDedicatedBucket() throws Exception {
        RateLimitingFilter filter = newFilter(5, 5, 5);

        MockHttpServletResponse uploadFirst = perform(filter, "POST", "/api/profile/avatar");
        MockHttpServletResponse uploadSecond = perform(filter, "POST", "/api/profile/avatar");
        MockHttpServletResponse deleteAvatar = perform(filter, "DELETE", "/api/profile/avatar");

        assertThat(uploadFirst.getStatus()).isEqualTo(200);
        assertThat(uploadSecond.getStatus()).isEqualTo(429);
        assertThat(uploadSecond.getContentAsString()).contains("Please try again in");
        assertThat(uploadSecond.getHeader("Retry-After")).isNotBlank();
        assertThat(deleteAvatar.getStatus()).isEqualTo(200);
    }

    private RateLimitingFilter newFilter(
            int authLoginRequestsPerMinute,
            int authRegisterRequestsPerMinute,
            int guestSessionCreateRequestsPerMinute) {
        RateLimitingProperties properties = new RateLimitingProperties();
        properties.setAuthRequestsPerMinute(10);
        properties.setAuthLoginRequestsPerMinute(authLoginRequestsPerMinute);
        properties.setAuthRegisterRequestsPerMinute(authRegisterRequestsPerMinute);
        properties.setGameRequestsPerMinute(10);
        properties.setGuestSessionCreateRequestsPerMinute(guestSessionCreateRequestsPerMinute);
        properties.setAvatarUploadRequestsPerMinute(1);

        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        when(clientIpResolver.resolveClientIp(org.mockito.ArgumentMatchers.any())).thenReturn("127.0.0.1");

        return new RateLimitingFilter(
                new ObjectMapper().registerModule(new JavaTimeModule()),
                properties,
                mock(RedisConnectionFactory.class),
                clientIpResolver);
    }

    private MockHttpServletResponse perform(RateLimitingFilter filter, String method, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
