package io.clueterra.questions.ai.service;

import io.clueterra.questions.ai.service.generation.AiGenerationHintHeuristics;
import io.clueterra.questions.entity.Country;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiGenerationHintHeuristicsTest {

    private final AiGenerationHintHeuristics heuristics = new AiGenerationHintHeuristics();

    @Test
    void isLowSignalDetectsCapitalOnlyHints() {
        assertThat(heuristics.isLowSignalHint("The capital city is Buenos Aires.")).isTrue();
        assertThat(heuristics.isLowSignalHint("The official currency is the Peso.")).isTrue();
        assertThat(heuristics.isLowSignalHint("The phone prefix is +54.")).isTrue();
    }

    @Test
    void isLowSignalAllowsInterestingHints() {
        assertThat(heuristics.isLowSignalHint(
                "The director of 'Parasite' made Oscar history here.")).isFalse();
        assertThat(heuristics.isLowSignalHint(
                "A water-dwelling rodent the size of a large dog roams freely here.")).isFalse();
    }

    @Test
    void isFormulaicDetectsBoringOpeners() {
        assertThat(heuristics.isFormulaicHint("This territory is located in the Pacific Ocean.")).isTrue();
        assertThat(heuristics.isFormulaicHint("Despite being small, the economy thrives.")).isTrue();
    }

    @Test
    void isFormulaicAllowsVividPhrasing() {
        assertThat(heuristics.isFormulaicHint(
                "Sushi, cherry blossoms, and bullet trains define daily life here.")).isFalse();
    }

    @Test
    void isBareStatisticDetectsNumericHeavyHints() {
        assertThat(heuristics.isBareStatisticClue("GDP growth was 3.7% last year with 12345 exports.")).isTrue();
        assertThat(heuristics.isBareStatisticClue("Population density is 214 per square kilometers.")).isTrue();
    }

    @Test
    void isGenericThemeDetectsOceanIsolationPattern() {
        assertThat(heuristics.isGenericThemeHint(
                "This island has no terrestrial borders and is surrounded by ocean.")).isTrue();
        assertThat(heuristics.isGenericThemeHint(
                "These scattered specks of land are managed by a distant power.")).isTrue();
    }

    @Test
    void isInterestingRequiresSpecificitySignals() {
        assertThat(heuristics.isInterestingClue(
                "Victor Hugo lived in exile here for fifteen years, writing Les Misérables.")).isTrue();
        assertThat(heuristics.isInterestingClue(
                "The Maho Beach is famous for low-flying jumbo jets landing nearby.")).isTrue();
    }

    @Test
    void isInterestingRejectsGenericClues() {
        assertThat(heuristics.isInterestingClue(
                "this small island has a tropical climate and warm weather year round")).isFalse();
    }

    @Test
    void isInterestingRejectsFormulaicClues() {
        assertThat(heuristics.isInterestingClue(
                "This territory is home to many Victor Hugo fans.")).isFalse();
    }

    @Test
    void countryDifficultyFloorScalesWithModifier() {
        assertThat(heuristics.countryDifficultyFloor(buildCountry(new BigDecimal("0.20")))).isEqualTo(12);
        assertThat(heuristics.countryDifficultyFloor(buildCountry(new BigDecimal("0.45")))).isEqualTo(16);
        assertThat(heuristics.countryDifficultyFloor(buildCountry(new BigDecimal("0.60")))).isEqualTo(22);
        assertThat(heuristics.countryDifficultyFloor(buildCountry(new BigDecimal("0.80")))).isEqualTo(30);
        assertThat(heuristics.countryDifficultyFloor(buildCountry(new BigDecimal("1.00")))).isEqualTo(38);
        assertThat(heuristics.countryDifficultyFloor(buildCountry(new BigDecimal("1.15")))).isEqualTo(46);
        assertThat(heuristics.countryDifficultyFloor(buildCountry(new BigDecimal("1.30")))).isEqualTo(54);
        assertThat(heuristics.countryDifficultyFloor(buildCountry(new BigDecimal("1.50")))).isEqualTo(60);
    }

    @Test
    void countryDifficultyFloorHandlesNullModifier() {
        Country country = new Country();
        country.setDifficultyModifier(null);
        assertThat(heuristics.countryDifficultyFloor(country)).isEqualTo(38);
    }

    @Test
    void countryDifficultyFloorHandlesNullCountry() {
        assertThat(heuristics.countryDifficultyFloor(null)).isEqualTo(38);
    }

    @Test
    void themeDifficultyDeltaHigherForHistoryThanTrivia() {
        int historyDelta = heuristics.themeDifficultyDelta("HISTORY", "HISTORICAL",
                "A significant battle took place here in World War II.");
        int triviaDelta = heuristics.themeDifficultyDelta("TRIVIA", "ABSOLUTE_FACT",
                "A fun random fact about the country.");

        assertThat(historyDelta).isGreaterThan(triviaDelta);
    }

    @Test
    void themeDifficultyDeltaPenalizesLowSignalHints() {
        int normalDelta = heuristics.themeDifficultyDelta("GENERAL", "ABSOLUTE_FACT",
                "A unique fact about this place.");
        int lowSignalDelta = heuristics.themeDifficultyDelta("GENERAL", "ABSOLUTE_FACT",
                "The official currency is the Dollar.");

        assertThat(lowSignalDelta).isLessThan(normalDelta);
    }

    @Test
    void factFamilyKeyDetectsAdminFacts() {
        assertThat(heuristics.factFamilyKey("GENERAL", "ABSOLUTE_FACT", "The capital city is Paris."))
                .isEqualTo("admin:capital");
        assertThat(heuristics.factFamilyKey("ECONOMY", "ABSOLUTE_FACT", "The currency is the Euro."))
                .isEqualTo("admin:currency");
    }

    @Test
    void factFamilyKeyDetectsGeographicIsolation() {
        assertThat(heuristics.factFamilyKey("GEOGRAPHY", "UNIQUE_FEATURE",
                "The island has no terrestrial borders."))
                .isEqualTo("geography:ocean_isolation");
    }

    @Test
    void extractKeywordsFiltersStopWords() {
        List<String> keywords = heuristics.extractKeywords(
                "This country is located around the world with major islands.");
        assertThat(keywords).doesNotContain("this", "country", "located", "around", "world", "islands", "major");
    }

    @Test
    void extractKeywordsKeepsContentWords() {
        List<String> keywords = heuristics.extractKeywords(
                "Victor Hugo lived in exile writing Les Misérables inside a decorated house.");
        assertThat(keywords).contains("victor", "hugo");
    }

    @Test
    void extractKeywordsLimitsTo8() {
        List<String> keywords = heuristics.extractKeywords(
                "Alpha bravo charlie delta echo foxtrot golf hotel india juliet kilo lima mike");
        assertThat(keywords).hasSizeLessThanOrEqualTo(8);
    }

    @Test
    void hintSimilarityScoreHighForIdenticalTexts() {
        assertThat(heuristics.hintSimilarityScore("same text", "same text")).isEqualTo(1.0);
    }

    @Test
    void hintSimilarityScoreHighForSameFactFamily() {
        double score = heuristics.hintSimilarityScore(
                "The capital city is located on the coast.",
                "The capital city sits by the ocean.");
        assertThat(score).isGreaterThan(0.4);
    }

    @Test
    void hintSimilarityScoreLowForDifferentTopics() {
        double score = heuristics.hintSimilarityScore(
                "Victor Hugo wrote Les Misérables here.",
                "The brown tree snake decimated the bird population.");
        assertThat(score).isLessThan(0.3);
    }

    @Test
    void targetDistinctCategoryCountScalesWithBatchSize() {
        assertThat(heuristics.targetDistinctCategoryCount(3)).isEqualTo(3);
        assertThat(heuristics.targetDistinctCategoryCount(6)).isEqualTo(3);
        assertThat(heuristics.targetDistinctCategoryCount(10)).isEqualTo(5);
    }

    @Test
    void targetImageClueCountTurnsShareIntoAnExplicitCount() {
        assertThat(heuristics.targetImageClueCount(10, 0.45)).isEqualTo(5);
        assertThat(heuristics.targetImageClueCount(3, 0.45)).isEqualTo(1);
        assertThat(heuristics.targetImageClueCount(10, 0.0)).isZero();
        assertThat(heuristics.targetImageClueCount(0, 0.45)).isZero();
    }

    private Country buildCountry(BigDecimal difficultyModifier) {
        Country country = new Country();
        country.setDifficultyModifier(difficultyModifier);
        return country;
    }
}
