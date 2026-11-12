package io.clueterra.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "clueterra.totp")
public class TotpProperties {

    private boolean enabled = true;
    private String encryptionKey;
}
