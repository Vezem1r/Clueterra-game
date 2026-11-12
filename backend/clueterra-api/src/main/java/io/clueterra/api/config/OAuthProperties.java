package io.clueterra.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "clueterra.oauth")
public class OAuthProperties {

    private String frontendRedirectUrl = "http://localhost:3001";
    private String backendBaseUrl = "http://localhost:8080";

    @NestedConfigurationProperty
    private OAuthProviderConfig github = new OAuthProviderConfig();

    @NestedConfigurationProperty
    private OAuthProviderConfig google = new OAuthProviderConfig();
}
