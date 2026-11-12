package io.clueterra.questions.clients.budgetprojects;

import io.clueterra.questions.entity.Country;
import io.clueterra.questions.service.hint.HintSanitizer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetProjectHintTextTest {

    @Test
    void combinesProjectNameYearPhysicalMetricAndFinalCost() {
        BudgetProjectHintFacts project = new BudgetProjectHintFacts(
                "Example Bridge", "BRIDGE", "COMPLETED", 2015,
                BigDecimal.valueOf(1_200_000_000), "FINAL_COST", "LENGTH", BigDecimal.valueOf(2_400));

        assertThat(BudgetProjectHintText.format(project))
                .contains("Example Bridge", "opened here in 2015", "2.4 km",
                        "final cost", "USD 1.2 billion");
    }

    @Test
    void doesNotInventCompletionYearForUnfinishedProject() {
        BudgetProjectHintFacts project = new BudgetProjectHintFacts(
                "Example Bridge", "BRIDGE", "UNDER_CONSTRUCTION", null,
                BigDecimal.valueOf(1_200_000_000), "CURRENT_ESTIMATE", "HEIGHT", BigDecimal.valueOf(350));

        assertThat(BudgetProjectHintText.format(project))
                .contains("under construction here", "350 metres", "current cost estimate")
                .doesNotContain("completed here", "in 2030");
    }

    @Test
    void keepsCurrencyCodeIntactWhenCountryCodeIsUs() {
        BudgetProjectHintFacts project = new BudgetProjectHintFacts(
                "The Shard", "SKYSCRAPER", "COMPLETED", null,
                BigDecimal.valueOf(1_200_000_000), "FINAL_COST", null, null);
        String text = BudgetProjectHintText.format(project);
        Country country = Country.builder().isoAlpha2("US").isoAlpha3("USA")
                .nameEn("United States").build();

        assertThat(new HintSanitizer().sanitize(text, country))
                .startsWith("The Shard is a skyscraper")
                .contains("USD 1.2 billion")
                .doesNotContain("The The", "this country$");
    }

    @Test
    void usesCorrectArticleAndAvoidsFalseAirportLabelForTransportHubs() {
        BudgetProjectHintFacts power = new BudgetProjectHintFacts(
                "Hydroelectric Plant", "ENERGY", "COMPLETED", null,
                null, null, null, null);
        BudgetProjectHintFacts hub = new BudgetProjectHintFacts(
                "World Trade Center Transportation Hub", "AIRPORT", "COMPLETED", null,
                null, null, null, null);

        assertThat(BudgetProjectHintText.format(power)).contains("is an energy project");
        assertThat(BudgetProjectHintText.format(hub))
                .contains("is a transport project")
                .doesNotContain("is an airport", "is a airport");
    }

    @Test
    void describesContractValueAsContractRatherThanFinalProjectCost() {
        BudgetProjectHintFacts project = new BudgetProjectHintFacts(
                "Gordie Howe International Bridge", "BRIDGE", "UNDER_CONSTRUCTION", null,
                BigDecimal.valueOf(4_700_000_000L), "CONTRACT_VALUE", null, null);

        assertThat(BudgetProjectHintText.format(project))
                .contains("A contract for it was valued at approximately USD 4.7 billion")
                .doesNotContain("final cost", "reported cost");
    }

    @Test
    void doesNotCallAirportLengthTheLengthOfTheWholeAirport() {
        BudgetProjectHintFacts project = new BudgetProjectHintFacts(
                "Airport of the Pacific", "AIRPORT", "COMPLETED", null,
                null, null, "LENGTH", BigDecimal.valueOf(2_400));

        assertThat(BudgetProjectHintText.format(project))
                .doesNotContain("stretches 2.4 km");
        assertThat(BudgetProjectHintText.hasVisibleMetric(project)).isFalse();
    }

    @Test
    void describesSmallSpacecraftLengthWithoutSayingItStretches() {
        BudgetProjectHintFacts project = new BudgetProjectHintFacts(
                "CloudSat", "SPACE", "COMPLETED", null,
                null, null, "LENGTH", BigDecimal.valueOf(2.3));

        assertThat(BudgetProjectHintText.format(project))
                .contains("It measures 2.3 metres in length")
                .doesNotContain("It stretches");
    }
}
