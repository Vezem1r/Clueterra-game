package io.clueterra.questions.service.challenge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.clueterra.questions.entity.Country;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChallengeCountryEntry(int order, Long countryId, String code, String name) {

    public static ChallengeCountryEntry of(int order, Country country) {
        return new ChallengeCountryEntry(order, country.getId(), country.getIsoAlpha2(), country.getNameEn());
    }

    public ChallengeCountryEntry withOrder(int newOrder) {
        return new ChallengeCountryEntry(newOrder, countryId, code, name);
    }
}
