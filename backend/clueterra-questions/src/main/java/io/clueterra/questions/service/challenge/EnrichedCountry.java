package io.clueterra.questions.service.challenge;

import io.clueterra.questions.config.CountryVisibilityProfile;
import io.clueterra.questions.entity.Country;

public record EnrichedCountry(Country country, CountryVisibilityProfile profile) {
}
