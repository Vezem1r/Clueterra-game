package io.clueterra.api.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OAuthProviderConfig {
    private String clientId = "";
    private String clientSecret = "";
}
