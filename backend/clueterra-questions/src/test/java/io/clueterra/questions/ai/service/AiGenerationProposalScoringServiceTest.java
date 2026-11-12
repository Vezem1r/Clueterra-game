package io.clueterra.questions.ai.service;

import io.clueterra.questions.ai.service.generation.AiGenerationHintHeuristics;
import io.clueterra.questions.ai.service.generation.AiGenerationProposalScoringService;
import io.clueterra.questions.entity.Country;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AiGenerationProposalScoringServiceTest {

    private final AiGenerationHintHeuristics heuristics = new AiGenerationHintHeuristics();
    private final AiGenerationProposalScoringService scoring = new AiGenerationProposalScoringService(heuristics);

    @Test
    void qualityNeverExceeds93() {
        double quality = scoring.recalibrateQuality(
                "The director of 'Parasite' made Oscar history here, sweeping four categories.",
                "Bong Joon-ho won Best Picture, Director, Original Screenplay, International Feature Film at 2020 Oscars",
                "Searched for Parasite, Oscar, Bong Joon-ho. No close matches found.",
                100.0
        );

        assertThat(quality).isLessThanOrEqualTo(93.0);
    }

    @Test
    void qualityNeverExceeds93EvenWithAllBonuses() {
        double quality = scoring.recalibrateQuality(
                "A high court once ruled that a Jaffa Cake is a cake, not a biscuit, for tax purposes.",
                "UK VAT tribunal ruled McVitie's Jaffa Cakes are cakes not biscuits in 1991, saving millions in tax",
                "Searched keywords: Jaffa Cake, biscuit, tax, court ruling. No duplicates found.",
                95.0
        );

        assertThat(quality).isLessThanOrEqualTo(93.0);
    }

    @Test
    void qualityDropsForLowSignalHints() {
        double quality = scoring.recalibrateQuality(
                "The capital city is located on the coast.",
                "Capital fact",
                null,
                80.0
        );

        assertThat(quality).isLessThan(50.0);
    }

    @Test
    void qualityDropsForFormulaicHints() {
        double quality = scoring.recalibrateQuality(
                "This territory is known for its unique geographic position in the Pacific.",
                "Geographic position fact",
                null,
                80.0
        );

        assertThat(quality).isLessThan(75.0);
    }

    @Test
    void qualityDropsWithoutEvidence() {
        double goodQuality = scoring.recalibrateQuality(
                "The world's oldest known recipe was found on clay tablets here.",
                "4,000-year-old lamb stew recipe found in ancient Mesopotamian clay tablets",
                null,
                80.0
        );
        double badQuality = scoring.recalibrateQuality(
                "The world's oldest known recipe was found on clay tablets here.",
                null,
                null,
                80.0
        );

        assertThat(goodQuality).isGreaterThan(badQuality);
    }

    @Test
    void qualityIncomingCappedAt88() {
        double q100 = scoring.recalibrateQuality(
                "A water-dwelling rodent the size of a large dog roams freely here.",
                "Capybara, native to South America",
                "Searched capybara, rodent. No duplicates.",
                100.0
        );
        double q88 = scoring.recalibrateQuality(
                "A water-dwelling rodent the size of a large dog roams freely here.",
                "Capybara, native to South America",
                "Searched capybara, rodent. No duplicates.",
                88.0
        );

        assertThat(q100).isEqualTo(q88);
    }

    @Test
    void difficultyRespectsCountryFloor() {
        Country famousCountry = buildCountry(new BigDecimal("0.20"));
        Country obscureCountry = buildCountry(new BigDecimal("1.50"));

        int famousDiff = scoring.recalibrateDifficulty(famousCountry, "NATURE", "UNIQUE_FEATURE",
                "A water-dwelling rodent the size of a large dog roams freely here.", 30);
        int obscureDiff = scoring.recalibrateDifficulty(obscureCountry, "NATURE", "UNIQUE_FEATURE",
                "A water-dwelling rodent the size of a large dog roams freely here.", 30);

        assertThat(famousDiff).isGreaterThanOrEqualTo(12);
        assertThat(obscureDiff).isGreaterThanOrEqualTo(60);
        assertThat(obscureDiff).isGreaterThan(famousDiff);
    }

    @Test
    void difficultyUsesGraduatedWeightsPreservingExpertRange() {
        Country country = buildCountry(new BigDecimal("0.20"));

        int lowProposed = scoring.recalibrateDifficulty(country, "NATURE", "UNIQUE_FEATURE",
                "A capybara roams freely here.", 10);
        int highProposed = scoring.recalibrateDifficulty(country, "NATURE", "UNIQUE_FEATURE",
                "A capybara roams freely here.", 90);

        assertThat(highProposed).isGreaterThan(lowProposed);
        assertThat(highProposed).isGreaterThanOrEqualTo(80);
        int diff = highProposed - lowProposed;
        assertThat(diff).isGreaterThan(40);
    }

    @Test
    void difficultyClampsBetween1And100() {
        Country country = buildCountry(new BigDecimal("0.20"));

        int veryLow = scoring.recalibrateDifficulty(country, "TRIVIA", "ABSOLUTE_FACT",
                "Everyone knows this.", 1);
        int veryHigh = scoring.recalibrateDifficulty(country, "HISTORY", "HISTORICAL",
                "An incredibly obscure historical fact about the strategic outpost.", 100);

        assertThat(veryLow).isGreaterThanOrEqualTo(1);
        assertThat(veryHigh).isLessThanOrEqualTo(100);
    }

    @Test
    void difficultyNullProposedFallsBackToThemeTarget() {
        Country country = buildCountry(new BigDecimal("0.80"));

        int withProposed = scoring.recalibrateDifficulty(country, "CULTURE", "CULTURAL",
                "Traditional dance is performed here.", 50);
        int withoutProposed = scoring.recalibrateDifficulty(country, "CULTURE", "CULTURAL",
                "Traditional dance is performed here.", null);

        assertThat(withoutProposed).isGreaterThan(0);
    }

    @Test
    void containsCountryNameDetectsCountry() {
        Country country = new Country();
        country.setNameEn("Brunei");

        assertThat(scoring.containsCountryName("The Brunei royal family plays polo.", country)).isTrue();
        assertThat(scoring.containsCountryName("The royal family plays polo internationally.", country)).isFalse();
    }

    @Test
    void boringCategoryPenalizesQuality() {
        double educationQuality = scoring.recalibrateQuality(
                "The university system provides access to higher education for all citizens.",
                "University enrollment statistics",
                null,
                80.0,
                "EDUCATION"
        );
        double cultureQuality = scoring.recalibrateQuality(
                "The university system provides access to higher education for all citizens.",
                "University enrollment statistics",
                null,
                80.0,
                "CULTURE"
        );

        assertThat(educationQuality).isLessThan(cultureQuality);
    }

    private Country buildCountry(BigDecimal difficultyModifier) {
        Country country = new Country();
        country.setDifficultyModifier(difficultyModifier);
        return country;
    }
}
