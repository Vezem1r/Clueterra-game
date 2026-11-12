package io.clueterra.questions.clients.owid.dto;

import java.util.Set;

public record OwidLicensePolicy(
        String attribution,
        String sourceUrl,
        String licenseLabel,
        String licenseUrl,
        Set<String> approvedProcessingLevels,
        Set<String> expectedProducers,
        Set<String> approvedOriginLicenseUrls) {

    public OwidLicensePolicy {
        approvedProcessingLevels = Set.copyOf(approvedProcessingLevels);
        expectedProducers = Set.copyOf(expectedProducers);
        approvedOriginLicenseUrls = Set.copyOf(approvedOriginLicenseUrls);
    }

    public boolean reliesOnOwidMajorProcessing() {
        return approvedProcessingLevels.equals(Set.of("major"));
    }
}
