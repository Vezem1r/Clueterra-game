package io.clueterra.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class OriginVerificationFilterTest {

    private static final String TRUSTED_ORIGIN = "https://clueterra.com";
    private static final String SESSION_COOKIE = "clueterra_access=token";

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private OriginVerificationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new OriginVerificationFilter(List.of(TRUSTED_ORIGIN, " http://localhost:3001/"), objectMapper);
    }

    @Test
    @DisplayName("safe methods pass even from a foreign origin")
    void safeMethodsAreNotChecked() throws Exception {
        MockHttpServletRequest request = request("GET");
        request.addHeader(HttpHeaders.ORIGIN, "https://evil.example");
        request.addHeader(HttpHeaders.COOKIE, SESSION_COOKIE);

        assertThat(passes(request)).isTrue();
    }

    @Test
    @DisplayName("a mutation from a configured origin passes")
    void trustedOriginPasses() throws Exception {
        MockHttpServletRequest request = request("POST");
        request.addHeader(HttpHeaders.ORIGIN, TRUSTED_ORIGIN);
        request.addHeader(HttpHeaders.COOKIE, SESSION_COOKIE);

        assertThat(passes(request)).isTrue();
    }

    @Test
    @DisplayName("configured origins are compared without whitespace, trailing slash or case")
    void configuredOriginsAreNormalized() throws Exception {
        MockHttpServletRequest request = request("DELETE");
        request.addHeader(HttpHeaders.ORIGIN, "http://LOCALHOST:3001");
        request.addHeader(HttpHeaders.COOKIE, SESSION_COOKIE);

        assertThat(passes(request)).isTrue();
    }

    @Test
    @DisplayName("a mutation from the API's own origin passes")
    void sameOriginPasses() throws Exception {
        MockHttpServletRequest request = request("PUT");
        request.setServerPort(8080);
        request.addHeader(HttpHeaders.ORIGIN, "http://localhost:8080");
        request.addHeader(HttpHeaders.COOKIE, SESSION_COOKIE);

        assertThat(passes(request)).isTrue();
    }

    @Test
    @DisplayName("a mutation from a foreign origin is rejected with the API error format")
    void foreignOriginIsRejected() throws Exception {
        MockHttpServletRequest request = request("POST");
        request.addHeader(HttpHeaders.ORIGIN, "https://evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.path("status").asInt()).isEqualTo(403);
        assertThat(body.path("code").asText()).isEqualTo("ORIGIN_NOT_ALLOWED");
    }

    @Test
    @DisplayName("an opaque null origin is rejected")
    void nullOriginIsRejected() throws Exception {
        MockHttpServletRequest request = request("POST");
        request.addHeader(HttpHeaders.ORIGIN, "null");
        request.addHeader(HttpHeaders.COOKIE, SESSION_COOKIE);

        assertThat(passes(request)).isFalse();
    }

    @Test
    @DisplayName("a sibling subdomain is not trusted")
    void siblingSubdomainIsRejected() throws Exception {
        MockHttpServletRequest request = request("POST");
        request.addHeader(HttpHeaders.ORIGIN, "https://evil.clueterra.com");
        request.addHeader(HttpHeaders.COOKIE, SESSION_COOKIE);

        assertThat(passes(request)).isFalse();
    }

    @Test
    @DisplayName("without Origin the Referer of a trusted page is accepted")
    void trustedRefererPasses() throws Exception {
        MockHttpServletRequest request = request("POST");
        request.addHeader(HttpHeaders.REFERER, TRUSTED_ORIGIN + "/profile?tab=settings");
        request.addHeader(HttpHeaders.COOKIE, SESSION_COOKIE);

        assertThat(passes(request)).isTrue();
    }

    @Test
    @DisplayName("without Origin a foreign Referer is rejected")
    void foreignRefererIsRejected() throws Exception {
        MockHttpServletRequest request = request("POST");
        request.addHeader(HttpHeaders.REFERER, "https://clueterra.com.evil.example/page");
        request.addHeader(HttpHeaders.COOKIE, SESSION_COOKIE);

        assertThat(passes(request)).isFalse();
    }

    @Test
    @DisplayName("a cookie-bearing mutation without Origin or Referer is rejected")
    void cookieWithoutSourceIsRejected() throws Exception {
        MockHttpServletRequest request = request("PATCH");
        request.addHeader(HttpHeaders.COOKIE, SESSION_COOKIE);

        assertThat(passes(request)).isFalse();
    }

    @Test
    @DisplayName("a cookie-bearing mutation with a malformed Referer is rejected")
    void malformedRefererIsRejected() throws Exception {
        MockHttpServletRequest request = request("POST");
        request.addHeader(HttpHeaders.REFERER, "not a url");
        request.addHeader(HttpHeaders.COOKIE, SESSION_COOKIE);

        assertThat(passes(request)).isFalse();
    }

    @Test
    @DisplayName("a mutation without cookies or browser origin headers passes")
    void credentialFreeClientPasses() throws Exception {
        assertThat(passes(request("POST"))).isTrue();
    }

    private static MockHttpServletRequest request(String method) {
        return new MockHttpServletRequest(method, "/api/profile");
    }

    private boolean passes(MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain.getRequest() != null;
    }
}
