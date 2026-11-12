package io.clueterra.api.legal.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.clueterra.api.config.LegalProperties;
import io.clueterra.api.exception.LegalVersionMismatchException;
import io.clueterra.api.legal.dto.LegalCurrentResponse;
import io.clueterra.api.legal.repository.LegalAcceptanceJdbcRepository;
import io.clueterra.api.security.UserPrincipal;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LegalAcceptanceService {

    public static final String SOURCE_REGISTER = "REGISTER";
    public static final String SOURCE_OAUTH_REGISTER = "OAUTH_REGISTER";
    public static final String SOURCE_USER_REACCEPT = "USER_REACCEPT";
    public static final String SOURCE_GUEST_START = "GUEST_START";
    public static final String SOURCE_GUEST_REACCEPT = "GUEST_REACCEPT";

    private final LegalProperties legalProperties;
    private final LegalAcceptanceJdbcRepository legalAcceptanceRepository;

    public LegalCurrentResponse getCurrent() {
        return new LegalCurrentResponse(
                legalProperties.getTermsVersion(),
                legalProperties.getPrivacyVersion(),
                legalProperties.getEffectiveDate());
    }

    public boolean isCurrent(String acceptedTermsVersion, String acceptedPrivacyVersion) {
        return legalProperties.isCurrent(acceptedTermsVersion, acceptedPrivacyVersion);
    }

    public boolean requiresAcceptance(UserPrincipal principal) {
        return principal != null && !isCurrent(principal.getTermsVersionAccepted(), principal.getPrivacyVersionAccepted());
    }

    public void validateSubmittedVersions(String termsVersion, String privacyVersion) {
        if (!legalProperties.getTermsVersion().equals(termsVersion)
                || !legalProperties.getPrivacyVersion().equals(privacyVersion)) {
            throw new LegalVersionMismatchException();
        }
    }

    @Transactional
    public void recordUserAcceptance(long userId, String termsVersion, String privacyVersion, String acceptanceSource) {
        validateSubmittedVersions(termsVersion, privacyVersion);

        Instant acceptedAt = Instant.now();
        legalAcceptanceRepository.updateUserAcceptance(userId, termsVersion, privacyVersion, acceptedAt);
        legalAcceptanceRepository.insertEvent(
                userId, null, acceptanceSource, termsVersion, privacyVersion, acceptedAt);
    }

}
