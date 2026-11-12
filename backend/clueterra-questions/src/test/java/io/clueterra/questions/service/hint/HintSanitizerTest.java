package io.clueterra.questions.service.hint;

import io.clueterra.questions.entity.Country;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HintSanitizerTest {

    private final HintSanitizer sanitizer = new HintSanitizer();

    @Test
    void sanitize_returnsNullForNullHint() {
        assertThat(sanitizer.sanitize(null, france())).isNull();
    }

    @Test
    void sanitize_returnsNullForNullCountry() {
        assertThat(sanitizer.sanitize("A republic with vineyards.", null)).isNull();
    }

    @Test
    void sanitize_replacesCountryNameAndCapitalizesResult() {
        String sanitized = sanitizer.sanitize("france has a rich literary history.", france());

        assertThat(sanitized).isEqualTo("This country has a rich literary history.");
    }

    @Test
    void sanitize_replacesNativeNameWhenDifferent() {
        String sanitized = sanitizer.sanitize("España exports olive oil worldwide.", spain());

        assertThat(sanitized).isEqualTo("This country exports olive oil worldwide.");
    }

    @Test
    void sanitize_replacesIso2AndSafeIso3CodesAndCleansSpacing() {
        String sanitized = sanitizer.sanitize("The FRA team beat FR , then advanced.", france());

        assertThat(sanitized).isEqualTo("This country team beat this country, then advanced.");
    }

    @Test
    void sanitize_doesNotReplaceRiskyAlpha3CodeAreForUnitedArabEmirates() {
        String sanitized = sanitizer.sanitize("WE ARE READY FOR THE FINAL.", uae());

        assertThat(sanitized).isEqualTo("WE ARE READY FOR THE FINAL.");
    }

    @Test
    void sanitize_doesNotReplaceRiskyAlpha3CodeAndForAndorra() {
        String sanitized = sanitizer.sanitize("BREAD AND BUTTER STILL SELLS.", andorra());

        assertThat(sanitized).isEqualTo("BREAD AND BUTTER STILL SELLS.");
    }

    @Test
    void sanitize_doesNotFalsePositiveOnSubstringInsideWoman() {
        String sanitized = sanitizer.sanitize("A woman-led cabinet changed the law.", oman());

        assertThat(sanitized).isEqualTo("A woman-led cabinet changed the law.");
    }

    @Test
    void sanitize_doesNotFalsePositiveOnSubstringInsideSomali() {
        String sanitized = sanitizer.sanitize("Somali trade routes once crossed this region.", mali());

        assertThat(sanitized).isEqualTo("Somali trade routes once crossed this region.");
    }

    @Test
    void sanitize_replacesMultiWordCountryNameWithArticle() {
        String sanitized = sanitizer.sanitize("The United States dominates this ranking.", unitedStates());

        assertThat(sanitized).isEqualTo("This country dominates this ranking.");
    }

    @Test
    void sanitize_returnsNullWhenCountryAdjectiveLeaks() {
        String sanitized = sanitizer.sanitize("French cuisine is one of the most famous in the world.", france());

        assertThat(sanitized).isNull();
    }

    @Test
    void sanitize_returnsNullWhenCountryVariationLeaks() {
        String sanitized = sanitizer.sanitize("Great Britain shaped much of this story.", unitedKingdom());

        assertThat(sanitized).isNull();
    }

    @Test
    void sanitize_returnsNullWhenTooManyDirectReplacementsWouldRemain() {
        String sanitized = sanitizer.sanitize("France, FR, FRA and France all point here.", france());

        assertThat(sanitized).isNull();
    }

    @Test
    void sanitize_preservesUnrelatedText() {
        String sanitized = sanitizer.sanitize("A republic with vineyards and a long culinary tradition.", france());

        assertThat(sanitized).isEqualTo("A republic with vineyards and a long culinary tradition.");
    }

    @Test
    void sanitize_cleansSpacingAroundPunctuation() {
        String sanitized = sanitizer.sanitize("FR , FR ; and vineyards !", france());

        assertThat(sanitized).isEqualTo("This country, this country; and vineyards!");
    }

    @Test
    void sanitize_preservesSpaceBeforeCountryCodeDomain() {
        String sanitized = sanitizer.sanitize(
                "Look for websites ending in .zw.",
                Country.builder().isoAlpha2("ZW").nameEn("Zimbabwe").build());

        assertThat(sanitized).isEqualTo("Look for websites ending in .zw.");
    }

    @Test
    void sanitize_handlesCountryWithOnlyNameData() {
        Country country = Country.builder()
                .nameEn("France")
                .build();

        String sanitized = sanitizer.sanitize("France has alpine borders.", country);

        assertThat(sanitized).isEqualTo("This country has alpine borders.");
    }

    @Test
    void sanitize_handlesCountryWithOnlyIsoData() {
        Country country = Country.builder()
                .isoAlpha2("FR")
                .build();

        String sanitized = sanitizer.sanitize("FR has alpine borders.", country);

        assertThat(sanitized).isEqualTo("This country has alpine borders.");
    }

    @Test
    void sanitize_returnsNullWhenLeakRemainsAfterReplacementPass() {
        String sanitized = sanitizer.sanitize("British politics shaped the system.", unitedKingdom());

        assertThat(sanitized).isNull();
    }

    @Test
    void sanitize_preservesParenthesesSpacing() {
        String sanitized = sanitizer.sanitize("FR ( a small state ) kept its monarchy.", france());

        assertThat(sanitized).isEqualTo("This country (a small state) kept its monarchy.");
    }

    private Country france() {
        return Country.builder()
                .isoAlpha2("FR")
                .isoAlpha3("FRA")
                .nameEn("France")
                .nameNative("France")
                .build();
    }

    private Country spain() {
        return Country.builder()
                .isoAlpha2("ES")
                .isoAlpha3("ESP")
                .nameEn("Spain")
                .nameNative("España")
                .build();
    }

    private Country oman() {
        return Country.builder()
                .isoAlpha2("OM")
                .isoAlpha3("OMN")
                .nameEn("Oman")
                .build();
    }

    private Country mali() {
        return Country.builder()
                .isoAlpha2("ML")
                .isoAlpha3("MLI")
                .nameEn("Mali")
                .build();
    }

    private Country unitedStates() {
        return Country.builder()
                .isoAlpha2("US")
                .isoAlpha3("USA")
                .nameEn("United States")
                .build();
    }

    private Country unitedKingdom() {
        return Country.builder()
                .isoAlpha2("GB")
                .isoAlpha3("GBR")
                .nameEn("United Kingdom")
                .build();
    }

    private Country uae() {
        return Country.builder()
                .isoAlpha2("AE")
                .isoAlpha3("ARE")
                .nameEn("United Arab Emirates")
                .build();
    }

    private Country andorra() {
        return Country.builder()
                .isoAlpha2("AD")
                .isoAlpha3("AND")
                .nameEn("Andorra")
                .build();
    }
}
