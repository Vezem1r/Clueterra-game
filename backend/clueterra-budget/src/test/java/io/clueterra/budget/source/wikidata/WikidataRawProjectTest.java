package io.clueterra.budget.source.wikidata;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.clueterra.budget.project.ProjectDatePrecision;
import io.clueterra.budget.project.ProjectMilestoneType;

class WikidataRawProjectTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("projectYears")
    @DisplayName("should resolve a playable project year without replacing Wikidata data")
    void shouldResolveProjectYear(
            String scenario,
            Integer existingYear,
            List<WikidataRawMilestone> milestones,
            Integer expectedYear) {
        WikidataRawProject project =
                new WikidataRawProject(
                        "Q1", "Q30", "US", List.of("File:Project.jpg"), existingYear, 10,
                        null, List.of(), List.of(), List.of(), List.of());

        WikidataRawProject enriched = project.withSupplementalData(List.of(), List.of(), milestones);

        assertThat(enriched.projectYear()).isEqualTo(expectedYear);
    }

    private static List<Arguments> projectYears() {
        return List.of(
                Arguments.of(
                        "Wikidata year wins",
                        1998,
                        List.of(milestone(ProjectMilestoneType.ACTUAL_COMPLETION, 2000)),
                        1998),
                Arguments.of(
                        "completion year is used",
                        null,
                        List.of(milestone(ProjectMilestoneType.ACTUAL_COMPLETION, 2000)),
                        2000),
                Arguments.of(
                        "exact opening date is used",
                        null,
                        List.of(
                                new WikidataRawMilestone(
                                        ProjectMilestoneType.OPENING,
                                        LocalDate.of(2017, 1, 1),
                                        null,
                                        ProjectDatePrecision.DAY,
                                        "Wikipedia",
                                        "https://en.wikipedia.org/",
                                        "https://en.wikipedia.org/",
                                        "WIKIPEDIA_INFOBOX:opened",
                                        "1 January 2017",
                                        true)),
                        2017),
                Arguments.of("missing milestones stay missing", null, List.of(), null));
    }

    private static WikidataRawMilestone milestone(ProjectMilestoneType type, int year) {
        return new WikidataRawMilestone(
                type,
                null,
                year,
                ProjectDatePrecision.YEAR,
                "Wikipedia",
                "https://en.wikipedia.org/",
                "https://en.wikipedia.org/",
                "WIKIPEDIA_INFOBOX:completed",
                Integer.toString(year),
                true);
    }
}
