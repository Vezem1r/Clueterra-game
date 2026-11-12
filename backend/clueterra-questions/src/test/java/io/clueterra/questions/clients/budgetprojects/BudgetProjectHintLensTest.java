package io.clueterra.questions.clients.budgetprojects;

import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.service.hint.DynamicDifficultyCalculator;
import io.clueterra.questions.service.hint.HintSanitizer;
import io.clueterra.questions.service.hint.lens.StatisticContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BudgetProjectHintLensTest {

    @Test
    void projectTierFromSourceStatisticsAffectsHintDifficulty() {
        DynamicDifficultyCalculator dynamic = mock(DynamicDifficultyCalculator.class);
        StatisticRepository repository = mock(StatisticRepository.class);
        when(dynamic.computeDifficulty(anyString(), eq(1), eq(43))).thenReturn(46);
        when(dynamic.computeQuality(anyString(), eq(1), eq(BigDecimal.valueOf(0.89))))
                .thenReturn(BigDecimal.valueOf(0.89));

        CountryStatistic easyImage = statistic("Q1", "PROJECT", "Famous Bridge", null);
        easyImage.setImageUrl("budget-projects/Q1/image.jpg");
        CountryStatistic hardImage = statistic("Q2", "PROJECT", "Obscure Bridge", null);
        hardImage.setImageUrl("budget-projects/Q2/image.jpg");
        when(repository.findCurrentByCountryIdAndSource(1L, DataSource.BUDGET_PROJECTS))
                .thenReturn(List.of(easyImage, hardImage,
                        statistic("Q1", "DIFFICULTY", "EASY", null),
                        statistic("Q2", "DIFFICULTY", "HARD", null),
                        statistic("Q1", "CATEGORY", "BRIDGE", null),
                        statistic("Q2", "CATEGORY", "BRIDGE", null)));

        BudgetProjectHintLens lens = new BudgetProjectHintLens(dynamic,
                new BudgetProjectDifficultyCalculator(dynamic), new HintSanitizer(), repository);
        Country country = Country.builder().id(1L).isoAlpha2("US")
                .difficultyModifier(new BigDecimal("0.20")).build();
        int easy = lens.generate(StatisticContext.builder().country(country).statistic(easyImage).build())
                .getFirst().estimatedDifficulty();
        int hard = lens.generate(StatisticContext.builder().country(country).statistic(hardImage).build())
                .getFirst().estimatedDifficulty();

        assertThat(easy).isEqualTo(25);
        assertThat(hard).isEqualTo(51);
    }

    @Test
    void omitsCountryNameRatherThanBreakingItInsideAProjectTitle() {
        DynamicDifficultyCalculator dynamic = mock(DynamicDifficultyCalculator.class);
        StatisticRepository repository = mock(StatisticRepository.class);
        when(dynamic.computeDifficulty(anyString(), eq(1), eq(43))).thenReturn(46);
        when(dynamic.computeQuality(anyString(), eq(1), eq(BigDecimal.valueOf(0.89))))
                .thenReturn(BigDecimal.valueOf(0.89));
        CountryStatistic image = statistic("Q3", "PROJECT", "Stade de France", null);
        image.setImageUrl("budget-projects/Q3/image.jpg");
        when(repository.findCurrentByCountryIdAndSource(1L, DataSource.BUDGET_PROJECTS))
                .thenReturn(List.of(image, statistic("Q3", "CATEGORY", "STADIUM", null)));
        BudgetProjectHintLens lens = new BudgetProjectHintLens(dynamic,
                new BudgetProjectDifficultyCalculator(dynamic), new HintSanitizer(), repository);
        Country country = Country.builder().id(1L).isoAlpha2("FR").isoAlpha3("FRA")
                .nameEn("France").build();

        String text = lens.generate(StatisticContext.builder().country(country).statistic(image).build())
                .getFirst().text();

        assertThat(text).startsWith("The pictured stadium is located here.")
                .doesNotContain("de this country", "France");
    }

    private CountryStatistic statistic(String projectId, String suffix, String text, BigDecimal value) {
        return CountryStatistic.builder()
                .countryId(1L)
                .source(DataSource.BUDGET_PROJECTS)
                .indicatorCode("BUDGET_PROJECT_" + projectId + ":" + suffix)
                .valueText(text)
                .value(value)
                .build();
    }
}
