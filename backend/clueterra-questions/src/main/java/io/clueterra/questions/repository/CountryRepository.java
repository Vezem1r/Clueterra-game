package io.clueterra.questions.repository;

import io.clueterra.questions.entity.Country;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CountryRepository {

    Country insert(Country country);

    Country fillMissingFromSource(Country existing, Country incoming);

    void incrementTimesFeatured(Long countryId);

    void adjustTimesFeatured(Long countryId, int delta);

    Optional<Country> findByIsoAlpha2(String isoAlpha2);

    Optional<Country> findByIsoAlpha3(String isoAlpha3);

    Optional<Country> findById(Long id);

    List<Country> findByIsActiveTrue();

    Map<Long, String> findAllCountryRegions();

    long count();

    long countByIsActiveTrue();

    Optional<String> findNameById(Long id);

    Map<Long, String> findAllCountryNames();
}
