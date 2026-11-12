package io.clueterra.budget.source.wikimedia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WikimediaLicensePolicyTest {

    private final WikimediaLicensePolicy policy = new WikimediaLicensePolicy();

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "CC0, CC0 1.0, https://creativecommons.org/publicdomain/zero/1.0/, ''",
            "CC BY, CC BY 4.0, https://creativecommons.org/licenses/by/4.0/, ''",
            "CC BY-SA, CC-BY-SA-2.0, https://creativecommons.org/licenses/by-sa/2.0/, ''",
            "Commons public domain, Public domain, https://commons.wikimedia.org/wiki/Template:PD-old, pd"
    })
    @DisplayName("should allow licenses that support commercial reuse")
    void shouldAllowCommerciallyReusableLicenses(
            String scenario, String label, String url, String licenseCode) {
        assertThat(policy.isAllowed(label, url, licenseCode)).as(scenario).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "non-commercial restriction, CC BY-NC 4.0, https://creativecommons.org/licenses/by-nc/4.0/, ''",
            "no-derivatives restriction, CC BY-ND 4.0, https://creativecommons.org/licenses/by-nd/4.0/, ''",
            "insecure URL, CC BY 4.0, http://creativecommons.org/licenses/by/4.0/, ''",
            "spoofed host, CC BY 4.0, https://creativecommons.org.example.com/licenses/by/4.0/, ''",
            "label and URL mismatch, CC0 1.0, https://creativecommons.org/licenses/by/4.0/, ''",
            "Commons public domain without pd code, Public domain, "
                    + "https://commons.wikimedia.org/wiki/Template:PD-old, ''"
    })
    @DisplayName("should reject licenses that are unsafe or cannot be verified")
    void shouldRejectUnsafeOrUnverifiableLicenses(
            String scenario, String label, String url, String licenseCode) {
        assertThat(policy.isAllowed(label, url, licenseCode)).as(scenario).isFalse();
    }
}
