package io.clueterra.api.admin.hints.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WikimediaLicensePolicyTest {

    private final WikimediaLicensePolicy policy = new WikimediaLicensePolicy();

    @Test
    void allowsCommercialCreativeCommonsAndPublicDomainLicenses() {
        assertThat(policy.isAllowed(
                "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0",
                "cc-by-sa-4.0"))
                .isTrue();
        assertThat(policy.isAllowed(
                "CC0 1.0",
                "https://creativecommons.org/publicdomain/zero/1.0/",
                "cc-zero"))
                .isTrue();
        assertThat(policy.isAllowed(
                "Public domain",
                "https://commons.wikimedia.org/wiki/Commons:Copyright_tags",
                "pd"))
                .isTrue();
    }

    @Test
    void rejectsRestrictedUnknownAndUntrustedLicenses() {
        assertThat(policy.isAllowed(
                "CC BY-NC 4.0",
                "https://creativecommons.org/licenses/by-nc/4.0",
                "cc-by-nc-4.0"))
                .isFalse();
        assertThat(policy.isAllowed("All rights reserved", "https://example.com/license", ""))
                .isFalse();
        assertThat(policy.isAllowed("CC BY 4.0", "http://creativecommons.org/licenses/by/4.0", ""))
                .isFalse();
    }
}
