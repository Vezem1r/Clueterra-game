package io.clueterra.questions.service.hint;

import io.clueterra.questions.entity.Country;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CountryNameMatcherTest {

    @Test
    void matchesWholeWordsOnly() {
        Country niger = Country.builder().isoAlpha2("NE").nameEn("Niger").build();

        assertThat(CountryNameMatcher.revealsCountry("This country borders Nigeria to the south.", niger)).isFalse();
        assertThat(CountryNameMatcher.revealsCountry("The Niger river crosses this country.", niger)).isTrue();
    }

    @Test
    void detectsDistinctivePartsOfCompoundNamesButNotGenericWords() {
        Country korea = Country.builder().isoAlpha2("KR").nameEn("Korea, Republic of").build();
        Country bosnia = Country.builder().isoAlpha2("BA").nameEn("Bosnia and Herzegovina").build();

        assertThat(CountryNameMatcher.revealsCountry("It became a republic of sorts in 1948.", korea)).isFalse();
        assertThat(CountryNameMatcher.revealsCountry("Seoul is the capital of Korea.", korea)).isTrue();
        assertThat(CountryNameMatcher.revealsCountry("Herzegovina is its southern region.", bosnia)).isTrue();
    }

    @Test
    void mentionsIsCaseInsensitive() {
        assertThat(CountryNameMatcher.mentions("FRANCE won the cup", "France")).isTrue();
        assertThat(CountryNameMatcher.mentions("A franchise model", "France")).isFalse();
    }
}
