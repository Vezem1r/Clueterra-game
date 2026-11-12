package io.clueterra.questions.service.hint;

import io.clueterra.questions.entity.enums.VisibilityTier;
import io.clueterra.questions.service.challenge.CountryVisibilityProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CountryDifficultyAdjuster {

    private final CountryVisibilityProfileService visibilityProfileService;

    public int adjust(String countryCode, int clueDifficulty) {
        VisibilityTier visibilityTier = visibilityProfileService
                .getProfile(countryCode)
                .visibilityTier();
        int countryBonus = switch (visibilityTier) {
            case EASY -> 0;
            case MEDIUM -> 10;
            case HARD -> 22;
            case EXPERT -> 35;
        };
        int countryFloor = switch (visibilityTier) {
            case EASY -> 1;
            case MEDIUM -> 30;
            case HARD -> 45;
            case EXPERT -> 60;
        };
        return Math.clamp(clueDifficulty + countryBonus, countryFloor, 95);
    }
}
