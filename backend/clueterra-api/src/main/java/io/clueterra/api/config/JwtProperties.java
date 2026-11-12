package io.clueterra.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "clueterra.jwt")
public class JwtProperties {

    private String secret;
    private long expiration = 900000;
    private long refreshExpiration = 604800000;
    private int refreshReuseGraceSeconds = 30;
}
