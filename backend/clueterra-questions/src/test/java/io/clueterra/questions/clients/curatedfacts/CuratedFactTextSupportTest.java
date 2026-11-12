package io.clueterra.questions.clients.curatedfacts;

import io.clueterra.questions.entity.Country;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CuratedFactTextSupportTest {

    @Test
    void removesLeakingItemsButKeepsSafeItemsFromLists() {
        Country cuba = country("CU", "Cuba");

        Optional<String> prepared = CuratedFactTextSupport.prepareValue(
                cuba,
                CuratedCountryFactIndicator.SIGNATURE_DRINK,
                "Mojito, Cuba Libre, Daiquiri");

        assertThat(prepared).contains("Mojito, Daiquiri");
        assertThat(CuratedFactTextSupport.formatValue(
                CuratedCountryFactIndicator.SIGNATURE_DRINK, prepared.orElseThrow()))
                .isEqualTo("Mojito and Daiquiri");
    }

    @Test
    void rejectsCountryNamesAdjectivesAndDerivedForms() {
        assertThat(CuratedFactTextSupport.prepareValue(
                country("PH", "Philippines"),
                CuratedCountryFactIndicator.EMBLEMATIC_BIRD,
                "Philippine eagle"))
                .isEmpty();
        assertThat(CuratedFactTextSupport.prepareValue(
                country("LI", "Liechtenstein"),
                CuratedCountryFactIndicator.TRADITIONAL_DANCE,
                "Liechtensteiner Polka"))
                .isEmpty();
        assertThat(CuratedFactTextSupport.prepareValue(
                country("VC", "Saint Vincent and the Grenadines"),
                CuratedCountryFactIndicator.EMBLEMATIC_ANIMAL,
                "St Vincent parrot"))
                .isEmpty();
    }

    @Test
    void listParserPreservesCommasInsideParentheses() {
        assertThat(CuratedFactTextSupport.splitList(
                "Khorovats (barbecue, grilled), Harisa (wheat porridge)"))
                .containsExactly("Khorovats (barbecue, grilled)", "Harisa (wheat porridge)");
    }

    @Test
    void recognizesExactAndNearDuplicateSymbols() {
        Country saintVincent = country("VC", "Saint Vincent and the Grenadines");

        assertThat(CuratedFactTextSupport.materiallyEquivalent(
                "Saint Vincent amazon parrot", "St Vincent parrot", saintVincent))
                .isTrue();
        assertThat(CuratedFactTextSupport.materiallyEquivalent(
                "Saker falcon", "Saker falcon", country("AE", "United Arab Emirates")))
                .isTrue();
        assertThat(CuratedFactTextSupport.materiallyEquivalent(
                "Saker falcon", "Ghaf tree", country("AE", "United Arab Emirates")))
                .isFalse();
    }

    private Country country(String code, String name) {
        return Country.builder()
                .isoAlpha2(code)
                .nameEn(name)
                .build();
    }
}
