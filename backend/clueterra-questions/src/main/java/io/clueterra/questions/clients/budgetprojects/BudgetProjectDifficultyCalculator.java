package io.clueterra.questions.clients.budgetprojects;

import io.clueterra.questions.service.hint.DynamicDifficultyCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
public class BudgetProjectDifficultyCalculator {

    private static final BigDecimal REFERENCE_COUNTRY_MODIFIER = new BigDecimal("0.65");
    private static final BigDecimal COUNTRY_SCALE = new BigDecimal("24");

    private final DynamicDifficultyCalculator dynamicDifficultyCalculator;

    public int calculate(String indicatorCode, BigDecimal countryModifier,
                         String catalogDifficulty, BudgetProjectHintFacts project) {
        int base = dynamicDifficultyCalculator.computeDifficulty(indicatorCode, 1, 43);
        BigDecimal modifier = countryModifier == null ? BigDecimal.ONE : countryModifier;
        int countryAdjustment = modifier.subtract(REFERENCE_COUNTRY_MODIFIER)
                .multiply(COUNTRY_SCALE)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
        int projectAdjustment = switch (String.valueOf(catalogDifficulty)) {
            case "EASY" -> -10;
            case "HARD" -> 16;
            default -> 2;
        };
        int clueAdjustment = (project.openingYear() == null ? 0 : -3)
                + (BudgetProjectHintText.hasVisibleMetric(project) ? -4 : 0);
        return Math.clamp(base + countryAdjustment + projectAdjustment + clueAdjustment, 15, 90);
    }
}
