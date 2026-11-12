package io.clueterra.questions.service.challenge;

import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.Hint;
import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintBand;
import io.clueterra.questions.entity.enums.HintType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ChallengeHintAllocationServiceTest {

    private final ChallengeHintAllocationService service = new ChallengeHintAllocationService();

    @Test
    void allocatesAgainstSlotBandsAndReturnsHintsHardToEasy() {
        Country country = country(1L, "US");
        ChallengePlan plan = new ChallengePlan(
                null,
                1,
                3,
                3,
                Map.of(
                        HintBand.HARD_HINT, 1,
                        HintBand.MEDIUM_HINT, 1,
                        HintBand.EASY_HINT, 1
                ),
                0,
                new int[]{85, 55, 20},
                new HintBand[][]{{HintBand.HARD_HINT, HintBand.MEDIUM_HINT, HintBand.EASY_HINT}},
                0
        );

        List<HintCandidate> candidates = List.of(
                HintCandidate.from(hint(101L, 82, "hard", DataSource.AI_GENERATED, "LAND_A", "LAND_A", Category.LANDMARKS, HintType.IMAGE_FACT, "img://1")),
                HintCandidate.from(hint(102L, 58, "medium", DataSource.WORLD_BANK, "WB_A", "WB_A", Category.ECONOMY, HintType.RANKING, null)),
                HintCandidate.from(hint(103L, 18, "easy", DataSource.NAGER_DATE, "HOLIDAY_A", "HOLIDAY_A", Category.GEOGRAPHY, HintType.ABSOLUTE_FACT, null)),
                HintCandidate.from(hint(104L, 12, "too easy", DataSource.CIA_FACTBOOK, "CIA_A", "CIA_A", Category.POLITICS, HintType.ABSOLUTE_FACT, null))
        );

        Map<Country, List<Hint>> allocated = service.allocateHints(plan, Map.of(country, candidates));

        assertThat(allocated.get(country))
                .extracting(Hint::getId)
                .containsExactly(101L, 102L, 103L);
        assertThat(allocated.get(country))
                .extracting(Hint::getDifficulty)
                .containsExactly(82, 58, 18);
    }

    @Test
    void respectsFamilyAndDatasourceCapsWhenPoolAllows() {
        Country country = country(2L, "FR");
        ChallengePlan plan = new ChallengePlan(
                null,
                1,
                3,
                3,
                Map.of(HintBand.EASY_HINT, 3),
                0,
                new int[]{30, 24, 18},
                new HintBand[][]{{HintBand.EASY_HINT, HintBand.EASY_HINT, HintBand.EASY_HINT}},
                0
        );

        List<HintCandidate> candidates = List.of(
                HintCandidate.from(hint(201L, 30, "geo one", DataSource.MANUAL, "MANUAL_CITY_1", "CITY", Category.GEOGRAPHY, HintType.ABSOLUTE_FACT, null)),
                HintCandidate.from(hint(202L, 29, "geo duplicate family", DataSource.MANUAL, "MANUAL_CITY_2", "CITY", Category.CULTURE, HintType.CULTURAL, null)),
                HintCandidate.from(hint(203L, 27, "econ", DataSource.MANUAL, "MANUAL_GDP_1", "GDP", Category.ECONOMY, HintType.RANKING, null)),
                HintCandidate.from(hint(204L, 25, "history", DataSource.WIKIDATA, "WD_HIST_1", "HIST", Category.HISTORY, HintType.HISTORICAL, null))
        );

        List<Hint> selected = service.allocateHints(plan, Map.of(country, candidates)).get(country);

        assertThat(selected).hasSize(3);
        assertThat(selected.stream().map(Hint::getSourceIndicator).collect(Collectors.toSet()))
                .doesNotContain("MANUAL_CITY_2");
        assertThat(selected.stream()
                .collect(Collectors.groupingBy(Hint::getSource, Collectors.counting())))
                .allSatisfy((source, count) -> assertThat(count).isLessThanOrEqualTo(2));
    }

    @Test
    void progressivelyRelaxesConstraintsButStillAvoidsDuplicateIndicators() {
        Country country = country(3L, "DE");
        ChallengePlan plan = new ChallengePlan(
                null,
                1,
                2,
                2,
                Map.of(HintBand.EASY_HINT, 2),
                0,
                new int[]{25, 15},
                new HintBand[][]{{HintBand.EASY_HINT, HintBand.EASY_HINT}},
                0
        );

        List<HintCandidate> candidates = List.of(
                HintCandidate.from(hint(301L, 26, "text one", DataSource.MANUAL, "REPEAT_HINT_1", "REPEAT", Category.GEOGRAPHY, HintType.ABSOLUTE_FACT, null)),
                HintCandidate.from(hint(302L, 22, "text two", DataSource.WORLD_BANK, "REPEAT_HINT_2", "REPEAT", Category.ECONOMY, HintType.RANKING, null))
        );

        List<Hint> selected = service.allocateHints(plan, Map.of(country, candidates)).get(country);

        assertThat(selected).hasSize(2);
        assertThat(selected)
                .extracting(Hint::getSourceIndicator)
                .containsExactly("REPEAT_HINT_1", "REPEAT_HINT_2");
    }

    private Country country(Long id, String iso2) {
        return Country.builder()
                .id(id)
                .isoAlpha2(iso2)
                .nameEn(iso2)
                .build();
    }

    private Hint hint(
            Long id,
            int difficulty,
            String text,
            DataSource source,
            String sourceIndicator,
            String familyMarker,
            Category category,
            HintType type,
            String imageUrl) {

        return Hint.builder()
                .id(id)
                .countryId(1L)
                .difficulty(difficulty)
                .qualityScore(new BigDecimal("0.90"))
                .hintText(text + " " + familyMarker)
                .source(source)
                .sourceIndicator(sourceIndicator)
                .category(category)
                .hintType(type)
                .imageUrl(imageUrl)
                .build();
    }
}
