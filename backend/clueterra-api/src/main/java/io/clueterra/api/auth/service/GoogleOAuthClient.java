package io.clueterra.api.auth.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import io.clueterra.api.auth.dto.google.GoogleTokenResponse;
import io.clueterra.api.auth.dto.google.GoogleUserResponse;
import io.netty.channel.ChannelOption;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import io.clueterra.api.auth.dto.OAuthUserInfo;
import io.clueterra.api.config.OAuthProperties;
import io.clueterra.api.config.OAuthProviderConfig;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class GoogleOAuthClient {

    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USER_URL = "https://www.googleapis.com/oauth2/v2/userinfo";

    private final OAuthProviderConfig config;
    private final WebClient webClient;

    public GoogleOAuthClient(OAuthProperties oauthProperties) {
        this.config = oauthProperties.getGoogle();
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(10));
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    public String buildAuthUrl(String redirectUri, String state) {
        return AUTH_URL + "?client_id=" + config.getClientId()
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=openid%20email%20profile"
                + "&access_type=offline"
                + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8)
                + "&prompt=select_account";
    }

    public OAuthUserInfo getUserInfo(String code, String redirectUri) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", config.getClientId());
        formData.add("client_secret", config.getClientSecret());
        formData.add("code", code);
        formData.add("redirect_uri", redirectUri);
        formData.add("grant_type", "authorization_code");

        GoogleTokenResponse tokenResponse = webClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(GoogleTokenResponse.class)
                .block(Duration.ofSeconds(15));

        if (tokenResponse == null || tokenResponse.accessToken() == null) {
            throw new IllegalStateException("Failed to retrieve access token from Google");
        }

        String accessToken = tokenResponse.accessToken();

        GoogleUserResponse userInfo = webClient.get()
                .uri(USER_URL)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(GoogleUserResponse.class)
                .block(Duration.ofSeconds(15));

        if (userInfo == null) {
            throw new IllegalStateException("Failed to retrieve user info from Google");
        }

        String email = userInfo.email();
        String name = userInfo.name();
        String avatarUrl = userInfo.picture();
        String oauthId = userInfo.id();
        return new OAuthUserInfo(email, name, avatarUrl, oauthId, userInfo.verifiedEmail());
    }
}
