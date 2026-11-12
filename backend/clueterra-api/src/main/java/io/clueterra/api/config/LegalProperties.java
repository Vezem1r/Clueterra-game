package io.clueterra.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "clueterra.legal")
public class LegalProperties {

    private String termsVersion = "2026-09-03";
    private String privacyVersion = "2026-09-13";
    private String effectiveDate = "September 13, 2026";

    public boolean isCurrent(String acceptedTermsVersion, String acceptedPrivacyVersion) {
        return termsVersion.equals(acceptedTermsVersion) && privacyVersion.equals(acceptedPrivacyVersion);
    }
}
