package io.clueterra.budget.source.wikidata;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.clueterra.budget.project.ProjectMetricType;

class WikidataMetricUnitFormatterTest {

    @Test
    @DisplayName("should render Wikidata kilometre values without exposing entity ids")
    void shouldRenderKilometresWithoutEntityIds() {
        String sourceUnit = WikidataMetricUnitFormatter.sourceUnit(
                ProjectMetricType.LENGTH,
                "Q828224",
                "kilometre",
                "Q11573");
        String evidenceText = WikidataMetricUnitFormatter.evidenceText(
                ProjectMetricType.LENGTH,
                new BigDecimal("106.800"),
                "Q828224",
                "kilometre",
                "Q11573");

        assertThat(sourceUnit).isEqualTo("KM");
        assertThat(evidenceText).isEqualTo("106.8 km");
    }

    @ParameterizedTest(name = "{0} should become {2} / {3}")
    @CsvSource({
            "Q3710, foot, FT, '328 ft'",
            "Q25343, square metre, M2, '328 m²'",
            "Q3320608, kilowatt, KW, '328 kW'",
            "Q11570, kilogram, KG, '328 kg'"
    })
    @DisplayName("should normalize common source unit labels")
    void shouldNormalizeCommonSourceUnits(
            String sourceUnitId,
            String sourceUnitLabel,
            String expectedUnit,
            String expectedEvidence) {
        assertThat(WikidataMetricUnitFormatter.sourceUnit(
                        ProjectMetricType.LENGTH,
                        sourceUnitId,
                        sourceUnitLabel,
                        "Q11573"))
                .isEqualTo(expectedUnit);
        assertThat(WikidataMetricUnitFormatter.evidenceText(
                        ProjectMetricType.LENGTH,
                        new BigDecimal("328"),
                        sourceUnitId,
                        sourceUnitLabel,
                        "Q11573"))
                .isEqualTo(expectedEvidence);
    }

    @Test
    @DisplayName("should describe unitless counts using the metric semantics")
    void shouldDescribeUnitlessCountsUsingMetricSemantics() {
        assertThat(WikidataMetricUnitFormatter.sourceUnit(
                        ProjectMetricType.CAPACITY,
                        "Q199",
                        "1",
                        "Q199"))
                .isEqualTo("SEAT");
        assertThat(WikidataMetricUnitFormatter.evidenceText(
                        ProjectMetricType.CAPACITY,
                        new BigDecimal("85000"),
                        "Q199",
                        "1",
                        "Q199"))
                .isEqualTo("85000 seats");
    }

    @Test
    @DisplayName("should keep an unknown human unit useful and bounded")
    void shouldKeepUnknownHumanUnitUsefulAndBounded() {
        String sourceUnit = WikidataMetricUnitFormatter.sourceUnit(
                ProjectMetricType.LENGTH,
                "Q999999999",
                "nautical mile used by an unusually verbose source definition",
                "Q11573");

        assertThat(sourceUnit)
                .isEqualTo("NAUTICAL_MILE_USED_BY_AN_UNUSUALLY_VERBO")
                .hasSizeLessThanOrEqualTo(40);
        assertThat(WikidataMetricUnitFormatter.evidenceText(
                        ProjectMetricType.LENGTH,
                        new BigDecimal("3"),
                        "Q999999999",
                        "nautical mile",
                        "Q11573"))
                .isEqualTo("3 nautical mile");
    }
}
