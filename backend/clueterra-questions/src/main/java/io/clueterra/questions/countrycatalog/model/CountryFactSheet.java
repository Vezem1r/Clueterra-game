package io.clueterra.questions.countrycatalog.model;

import java.util.Map;

public record CountryFactSheet(
        Map<String, String> capital,
        CountryPopulationFact population,
        CountryAreaFact areaKm2,
        CountryPeakFact highestPoint,
        boolean landlocked,
        boolean drivesLeft,
        CountryRankFact areaRank,
        CountryRankFact populationRank) {

    public CountryFactSheet withRanks(CountryRankFact area, CountryRankFact population) {
        return new CountryFactSheet(capital, this.population, areaKm2, highestPoint, landlocked, drivesLeft, area, population);
    }
}
