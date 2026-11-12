package io.clueterra.questions.service.hint;

import io.clueterra.questions.repository.IndicatorConfigRecord;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public final class IndicatorConfig {

    private static IndicatorConfig INSTANCE;
    private final IndicatorConfigService service;

    @PostConstruct
    void register() {
        INSTANCE = this;
    }

    private static IndicatorConfigService requireService() {
        if (INSTANCE == null) {
            throw new IllegalStateException("IndicatorConfig not yet initialized by Spring");
        }
        return INSTANCE.service;
    }

    public static boolean isExcludedFromRanking(String indicatorCode) {
        if (indicatorCode == null) return false;
        return requireService().isExcludedFromRanking(indicatorCode);
    }

    public static boolean isExcludedFromPercentile(String indicatorCode) {
        if (indicatorCode == null) return false;
        return requireService().isExcludedFromPercentile(indicatorCode);
    }

    public static boolean isHighProfile(String indicatorCode) {
        if (indicatorCode == null) return false;
        return requireService().isHighProfile(indicatorCode);
    }

    public static boolean isMediumProfile(String indicatorCode) {
        if (indicatorCode == null) return false;
        return requireService().isMediumProfile(indicatorCode);
    }

    public static boolean isHighInterest(String indicatorCode) {
        if (indicatorCode == null) return false;
        return requireService().isHighInterest(indicatorCode);
    }

    public static String getDisplayName(String indicatorCode, String fallback) {
        if (indicatorCode == null) return fallback;
        return requireService().getDisplayName(indicatorCode, fallback);
    }

    public static Optional<IndicatorConfigRecord> getConfig(String indicatorCode) {
        return requireService().getConfig(indicatorCode);
    }

    public static boolean hasTemplate(String indicatorCode) {
        return requireService().hasTemplate(indicatorCode);
    }

    public static String getTemplate(String indicatorCode) {
        return requireService().getTemplate(indicatorCode);
    }

    public static int getBaseDifficulty(String indicatorCode, int defaultValue) {
        return requireService().getBaseDifficulty(indicatorCode, defaultValue);
    }

    public static BigDecimal getQualityScore(String indicatorCode, BigDecimal defaultValue) {
        return requireService().getQualityScore(indicatorCode, defaultValue);
    }

    public static String getUnit(String indicatorCode) {
        return requireService().getUnit(indicatorCode);
    }
}
