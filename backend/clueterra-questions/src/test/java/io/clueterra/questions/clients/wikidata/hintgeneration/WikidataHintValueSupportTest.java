package io.clueterra.questions.clients.wikidata.hintgeneration;

import io.clueterra.questions.entity.Country;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WikidataHintValueSupportTest {

    @Test
    void plugStandardsBecomeShortFamiliarTypeNames() {
        List<String> types = WikidataHintValueSupport.preparePlugTypes(
                "AC power plugs and sockets: British and related types, "
                        + "BS 1363, Europlug, NEMA 1-15, Schuko");

        assertThat(types).containsExactly("Type G", "Type C", "Type A");
        assertThat(WikidataHintValueSupport.formatPlugTypes(types))
                .isEqualTo("Type G, Type C, and Type A");
    }

    @Test
    void answerRevealingAndGenericAnthemsAreRejected() {
        Country zimbabwe = country("ZW", "Zimbabwe");
        Country southAfrica = country("ZA", "South Africa");
        Country kosovo = country("XK", "Kosovo");

        assertThat(WikidataHintValueSupport.prepareAnthem(
                zimbabwe, "National Anthem of Zimbabwe")).isEmpty();
        assertThat(WikidataHintValueSupport.prepareAnthem(
                southAfrica, "national anthem of Mzansi")).isEmpty();
        assertThat(WikidataHintValueSupport.prepareAnthem(kosovo, "Europe")).isEmpty();
        assertThat(WikidataHintValueSupport.prepareAnthem(
                country("AS", "American Samoa"), "Amerika Samoa, The Star-Spangled Banner"))
                .isEmpty();
        assertThat(WikidataHintValueSupport.prepareAnthem(
                country("LB", "Lebanon"), "Lebanese national anthem"))
                .isEmpty();
        assertThat(WikidataHintValueSupport.prepareAnthem(
                country("BH", "Bahrain"), "Bahrainona"))
                .isEmpty();
    }

    @Test
    void distinctiveAnthemIsKept() {
        assertThat(WikidataHintValueSupport.prepareAnthem(
                country("US", "United States"), "The Star-Spangled Banner"))
                .contains("The Star-Spangled Banner");
    }

    @Test
    void domainListKeepsFirstNonRevealingCountryDomain() {
        assertThat(WikidataHintValueSupport.prepareDomain(
                country("CH", "Switzerland"), ".ch, .swiss"))
                .contains(".ch");
    }

    @Test
    void genericAnthemMatchingIgnoresDiacritics() {
        assertThat(WikidataHintValueSupport.prepareAnthem(
                country("XX", "Example"), "Liberté"))
                .isEmpty();
    }

    private Country country(String code, String name) {
        return Country.builder()
                .isoAlpha2(code)
                .nameEn(name)
                .build();
    }
}
