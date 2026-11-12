package io.clueterra.questions.clients.brands.hintgeneration;

import io.clueterra.questions.clients.brands.dto.BrandRow;
import io.clueterra.questions.clients.brands.repository.BrandRepository;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.service.hint.DynamicDifficultyCalculator;
import io.clueterra.questions.service.hint.IndicatorConfigTestSupport;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import io.clueterra.questions.service.hint.lens.StatisticContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrandLensTest {

    @AfterEach
    void tearDown() {
        IndicatorConfigTestSupport.clear();
    }

    @Test
    void iconicBrandUsesVerifiableOriginInsteadOfMarketingHyperbole() {
        IndicatorConfigTestSupport.installTemplates(Map.of(
                "BRAND_ICONIC_1", "Ever heard of {brand_1}? Of course you have — billions use it."));
        BrandRepository repository = mock(BrandRepository.class);
        when(repository.findLastFetched()).thenReturn(List.of(
                new BrandRow("ES", "Example Brand", "tech", 1998, 8, 1, true, null)));
        BrandAmbiguityCalculator ambiguityCalculator = mock(BrandAmbiguityCalculator.class);
        when(ambiguityCalculator.compute(anyString(), anyString(), any(), anyString())).thenReturn(0);

        List<CandidateHint> hints = new BrandLens(repository, ambiguityCalculator,
                new DynamicDifficultyCalculator(), Clock.systemUTC()).generate(context());

        assertThat(hints).singleElement().satisfies(hint -> {
            assertThat(hint.sourceIndicator()).isEqualTo("BRAND_ICONIC_1");
            assertThat(hint.text()).contains("Example Brand", "1998")
                    .doesNotContain("Of course", "billions");
        });
    }

    @Test
    void industryPairKeepsTheBrandAnchorWithoutUnsupportedGlobalReach() {
        IndicatorConfigTestSupport.installTemplates(Map.of(
                "BRAND_TECH_POWERHOUSE", "Your entire daily routine is shaped by {brand_1} and {brand_2}."));
        BrandRepository repository = mock(BrandRepository.class);
        when(repository.findLastFetched()).thenReturn(List.of(
                new BrandRow("ES", "First Brand", "tech", 1998, 8, 1, true, null),
                new BrandRow("ES", "Second Brand", "tech", 2005, 7, 2, true, null)));
        BrandAmbiguityCalculator ambiguityCalculator = mock(BrandAmbiguityCalculator.class);
        when(ambiguityCalculator.compute(anyString(), anyString(), any(), anyString())).thenReturn(0);

        List<CandidateHint> hints = new BrandLens(repository, ambiguityCalculator,
                new DynamicDifficultyCalculator(), Clock.systemUTC()).generate(context());

        assertThat(hints).singleElement().satisfies(hint -> {
            assertThat(hint.sourceIndicator()).isEqualTo("BRAND_TECH_POWERHOUSE");
            assertThat(hint.text()).contains("First Brand", "Second Brand", "technology")
                    .doesNotContain("entire daily routine", "massive");
        });
    }

    private StatisticContext context() {
        Country country = Country.builder()
                .id(3L).isoAlpha2("ES").isoAlpha3("ESP")
                .nameEn("Spain").region("Europe").subregion("Southern Europe").build();
        CountryStatistic statistic = CountryStatistic.builder()
                .countryId(3L).source(DataSource.GLOBAL_BRANDS).indicatorCode("BRAND_COUNT")
                .indicatorName("brand count").category(Category.CULTURE).year(2026).build();
        return new StatisticContext(country, statistic, 120, 180, "BRAND_COUNT", true,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
    }
}
