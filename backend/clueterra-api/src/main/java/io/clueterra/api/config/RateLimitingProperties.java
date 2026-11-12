package io.clueterra.api.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "clueterra.rate-limiting")
@Getter
@Setter
public class RateLimitingProperties {

    private int gameRequestsPerMinute = 60;
    private int budgetRequestsPerMinute = 120;
    private int authRequestsPerMinute = 10;
    private int authLoginRequestsPerMinute = 5;
    private int authLoginAttemptsPerAccount = 10;
    private Duration authLoginAccountWindow = Duration.ofMinutes(15);
    private int authRegisterRequestsPerMinute = 3;
    private int guestSessionCreateRequestsPerMinute = 5;
    private int avatarUploadRequestsPerMinute = 1;
    private int assetRequestsPerMinute = 360;
    private int shareRequestsPerMinute = 30;
    private int friendRequestsPerMinute = 10;
    private int adminRequestsPerMinute = 120;
    private int defaultRequestsPerMinute = 100;
    private String keySalt = "";
    private List<String> trustedProxies = List.of();
}
