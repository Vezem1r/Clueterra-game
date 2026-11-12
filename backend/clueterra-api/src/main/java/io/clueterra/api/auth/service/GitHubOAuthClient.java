package io.clueterra.api.auth.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.clueterra.api.auth.dto.github.GitHubEmailResponse;
import io.clueterra.api.auth.dto.github.GitHubTokenResponse;
import io.clueterra.api.auth.dto.github.GitHubUserResponse;
import io.netty.channel.ChannelOption;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import io.clueterra.api.auth.dto.OAuthUserInfo;
import io.clueterra.api.config.OAuthProperties;
import io.clueterra.api.config.OAuthProviderConfig;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class GitHubOAuthClient {

    private static final String AUTH_URL = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";
    private static final String EMAILS_URL = "https://api.github.com/user/emails";

    private final OAuthProviderConfig config;
    private final WebClient webClient;

    public GitHubOAuthClient(OAuthProperties oauthProperties) {
        this.config = oauthProperties.getGithub();
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
                + "&scope=user:email"
                + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8)
                + "&prompt=consent";
    }

    public OAuthUserInfo getUserInfo(String code, String redirectUri) {
        GitHubTokenResponse tokenResponse = webClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "client_id", config.getClientId(),
                        "client_secret", config.getClientSecret(),
                        "code", code,
                        "redirect_uri", redirectUri))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(GitHubTokenResponse.class)
                .block(Duration.ofSeconds(15));

        if (tokenResponse == null || tokenResponse.accessToken() == null) {
            throw new IllegalStateException("Failed to retrieve access token from GitHub");
        }

        String accessToken = tokenResponse.accessToken();

        GitHubUserResponse userInfo = webClient.get()
                .uri(USER_URL)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(GitHubUserResponse.class)
                .block(Duration.ofSeconds(15));

        if (userInfo == null) {
            throw new IllegalStateException("Failed to retrieve user info from GitHub");
        }

        String email = userInfo.email();
        boolean emailVerified = false;

        List<GitHubEmailResponse> emails = webClient.get()
                .uri(EMAILS_URL)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<GitHubEmailResponse>>() {
                })
                .block(Duration.ofSeconds(15));

        if (emails != null) {
            GitHubEmailResponse primaryVerified = emails.stream()
                    .filter(GitHubEmailResponse::primary)
                    .filter(GitHubEmailResponse::verified)
                    .findFirst()
                    .orElse(null);
            if (primaryVerified != null) {
                email = primaryVerified.email();
                emailVerified = true;
            } else if (email == null) {
                email = emails.stream()
                        .filter(GitHubEmailResponse::primary)
                        .map(GitHubEmailResponse::email)
                        .findFirst()
                        .orElse(null);
            }
        }

        if (email == null) {
            throw new IllegalStateException("GitHub account does not have a primary email address");
        }

        String displayName = userInfo.name() != null ? userInfo.name() : userInfo.login();
        String avatarUrl = userInfo.avatarUrl();
        String oauthId = String.valueOf(userInfo.id());
        return new OAuthUserInfo(email, displayName, avatarUrl, oauthId, emailVerified);
    }
}
