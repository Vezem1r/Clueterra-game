package io.clueterra.questions.service.challenge;

import io.clueterra.questions.config.ChallengeProfile;
import io.clueterra.questions.config.ChallengeProfileFactory;
import io.clueterra.questions.entity.enums.ChallengeLevel;
import io.clueterra.questions.entity.enums.HintBand;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChallengePlanBuilderTest {

    @Test
    void mediumPlanAlignsSlotBandsWithGlobalQuotaDistribution() {
        ChallengeProfile profile = ChallengeProfileFactory.forLevel(ChallengeLevel.MEDIUM);

        ChallengePlan plan = ChallengePlanBuilder.build(profile, 5, 6);

        assertThat(plan.slotDifficultyTargets()).containsExactly(75, 67, 55, 42, 26, 10);
        assertBandCounts(plan, Map.of(
                HintBand.HARD_HINT, 6L,
                HintBand.MEDIUM_HINT, 12L,
                HintBand.EASY_HINT, 12L
        ));
        assertThat(Arrays.asList(plan.slotBandTargets()))
                .allSatisfy(countrySlots -> assertThat(countrySlots)
                        .isSortedAccordingTo((left, right) -> Integer.compare(right.ordinal(), left.ordinal())));
    }

    @Test
    void hardPlanProducesNoEasySlots() {
        ChallengeProfile profile = ChallengeProfileFactory.forLevel(ChallengeLevel.HARD);

        ChallengePlan plan = ChallengePlanBuilder.build(profile, 5, 6);

        assertThat(plan.slotDifficultyTargets()).containsExactly(98, 91, 82, 71, 58, 45);
        assertBandCounts(plan, Map.of(
                HintBand.HARD_HINT, 18L,
                HintBand.MEDIUM_HINT, 12L
        ));
        assertThat(Arrays.stream(plan.slotBandTargets())
                .flatMap(Arrays::stream))
                .doesNotContain(HintBand.EASY_HINT);
    }

    private void assertBandCounts(ChallengePlan plan, Map<HintBand, Long> expected) {
        Map<HintBand, Long> actual = Arrays.stream(plan.slotBandTargets())
                .flatMap(Arrays::stream)
                .collect(java.util.stream.Collectors.groupingBy(
                        band -> band,
                        java.util.stream.Collectors.counting()
                ));
        assertThat(actual).containsAllEntriesOf(expected);
    }
}
