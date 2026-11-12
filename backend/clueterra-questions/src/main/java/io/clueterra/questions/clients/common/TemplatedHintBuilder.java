package io.clueterra.questions.clients.common;

import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.service.hint.DifficultyCalculator;
import io.clueterra.questions.service.hint.DynamicDifficultyCalculator;
import io.clueterra.questions.service.hint.IndicatorConfig;
import io.clueterra.questions.service.hint.IndicatorTemplateRenderer;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import io.clueterra.questions.service.hint.lens.StatisticContext;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

public final class TemplatedHintBuilder {

    private TemplatedHintBuilder() {
    }

    public static Optional<CandidateHint> buildTemplatedCandidate(
            String templateCode,
            String sourceIndicator,
            Map<String, ?> placeholders,
            HintType type,
            Category category,
            DataSource source,
            Integer sourceYear,
            int difficulty,
            BigDecimal quality,
            int ambiguity,
            String lensName,
            String imageUrl) {
        if (!IndicatorConfig.hasTemplate(templateCode)) {
            return Optional.empty();
        }

        String text = IndicatorTemplateRenderer.render(IndicatorConfig.getTemplate(templateCode), placeholders);
        return Optional.of(buildCandidate(
                text,
                type,
                category,
                source,
                sourceIndicator,
                sourceYear,
                difficulty,
                quality,
                ambiguity,
                lensName,
                imageUrl
        ));
    }

    public static Optional<CandidateHint> buildTemplatedCandidate(
            String indicatorCode,
            Map<String, ?> placeholders,
            HintType type,
            Category category,
            DataSource source,
            Integer sourceYear,
            int difficulty,
            BigDecimal quality,
            int ambiguity,
            String lensName,
            String imageUrl) {
        return buildTemplatedCandidate(
                indicatorCode,
                indicatorCode,
                placeholders,
                type,
                category,
                source,
                sourceYear,
                difficulty,
                quality,
                ambiguity,
                lensName,
                imageUrl
        );
    }

    public static Optional<CandidateHint> buildTemplatedCandidate(
            String indicatorCode,
            Map<String, ?> placeholders,
            HintType type,
            Category category,
            DataSource source,
            Integer sourceYear,
            int difficulty,
            BigDecimal quality,
            int ambiguity,
            String lensName) {
        return buildTemplatedCandidate(
                indicatorCode,
                indicatorCode,
                placeholders,
                type,
                category,
                source,
                sourceYear,
                difficulty,
                quality,
                ambiguity,
                lensName,
                null
        );
    }

    public static Optional<CandidateHint> buildDynamicTemplatedCandidate(
            DynamicDifficultyCalculator difficultyCalculator,
            String templateCode,
            String sourceIndicator,
            Map<String, ?> placeholders,
            HintType type,
            Category category,
            DataSource source,
            Integer sourceYear,
            int ambiguity,
            int baseDifficulty,
            BigDecimal baseQuality,
            String lensName,
            String imageUrl) {
        return buildTemplatedCandidate(
                templateCode,
                sourceIndicator,
                placeholders,
                type,
                category,
                source,
                sourceYear,
                difficultyCalculator.computeDifficulty(sourceIndicator, ambiguity, baseDifficulty),
                difficultyCalculator.computeQuality(sourceIndicator, ambiguity, baseQuality),
                ambiguity,
                lensName,
                imageUrl
        );
    }

    public static Optional<CandidateHint> buildDynamicTemplatedCandidate(
            DynamicDifficultyCalculator difficultyCalculator,
            String indicatorCode,
            Map<String, ?> placeholders,
            HintType type,
            Category category,
            DataSource source,
            Integer sourceYear,
            int ambiguity,
            int baseDifficulty,
            BigDecimal baseQuality,
            String lensName,
            String imageUrl) {
        return buildDynamicTemplatedCandidate(
                difficultyCalculator,
                indicatorCode,
                indicatorCode,
                placeholders,
                type,
                category,
                source,
                sourceYear,
                ambiguity,
                baseDifficulty,
                baseQuality,
                lensName,
                imageUrl
        );
    }

    public static Optional<CandidateHint> buildDynamicTemplatedCandidate(
            DynamicDifficultyCalculator difficultyCalculator,
            String templateCode,
            String sourceIndicator,
            Map<String, ?> placeholders,
            HintType type,
            Category category,
            DataSource source,
            Integer sourceYear,
            int ambiguity,
            int baseDifficulty,
            BigDecimal baseQuality,
            String lensName) {
        return buildDynamicTemplatedCandidate(
                difficultyCalculator,
                templateCode,
                sourceIndicator,
                placeholders,
                type,
                category,
                source,
                sourceYear,
                ambiguity,
                baseDifficulty,
                baseQuality,
                lensName,
                null
        );
    }

    public static Optional<CandidateHint> buildDynamicTemplatedCandidate(
            DynamicDifficultyCalculator difficultyCalculator,
            String indicatorCode,
            Map<String, ?> placeholders,
            HintType type,
            Category category,
            DataSource source,
            Integer sourceYear,
            int ambiguity,
            int baseDifficulty,
            BigDecimal baseQuality,
            String lensName) {
        return buildDynamicTemplatedCandidate(
                difficultyCalculator,
                indicatorCode,
                indicatorCode,
                placeholders,
                type,
                category,
                source,
                sourceYear,
                ambiguity,
                baseDifficulty,
                baseQuality,
                lensName,
                null
        );
    }

    public static Optional<CandidateHint> buildConfiguredStaticTemplatedCandidate(
            StatisticContext ctx,
            String templateCode,
            String sourceIndicator,
            Map<String, ?> placeholders,
            Category category,
            HintType type,
            int ambiguity,
            int defaultBaseDifficulty,
            BigDecimal defaultQuality,
            String lensName,
            String imageUrl) {
        return buildTemplatedCandidate(
                templateCode,
                sourceIndicator,
                placeholders,
                type,
                category,
                ctx.statistic().getSource(),
                ctx.statistic().getYear(),
                DifficultyCalculator.calculate(ctx, IndicatorConfig.getBaseDifficulty(sourceIndicator, defaultBaseDifficulty)),
                IndicatorConfig.getQualityScore(sourceIndicator, defaultQuality),
                ambiguity,
                lensName,
                imageUrl
        );
    }

    public static Optional<CandidateHint> buildConfiguredStaticTemplatedCandidate(
            StatisticContext ctx,
            String templateCode,
            Map<String, ?> placeholders,
            Category category,
            HintType type,
            int ambiguity,
            int defaultBaseDifficulty,
            BigDecimal defaultQuality,
            String lensName) {
        return buildConfiguredStaticTemplatedCandidate(
                ctx,
                templateCode,
                templateCode,
                placeholders,
                category,
                type,
                ambiguity,
                defaultBaseDifficulty,
                defaultQuality,
                lensName,
                null
        );
    }

    public static Optional<CandidateHint> buildConfiguredStaticTemplatedCandidate(
            StatisticContext ctx,
            String templateCode,
            String sourceIndicator,
            Map<String, ?> placeholders,
            HintType type,
            int ambiguity,
            int defaultBaseDifficulty,
            BigDecimal defaultQuality,
            String lensName,
            String imageUrl) {
        return buildTemplatedCandidate(
                templateCode,
                sourceIndicator,
                placeholders,
                type,
                ctx.statistic().getCategory(),
                ctx.statistic().getSource(),
                ctx.statistic().getYear(),
                DifficultyCalculator.calculate(ctx, IndicatorConfig.getBaseDifficulty(sourceIndicator, defaultBaseDifficulty)),
                IndicatorConfig.getQualityScore(sourceIndicator, defaultQuality),
                ambiguity,
                lensName,
                imageUrl
        );
    }

    public static Optional<CandidateHint> buildConfiguredStaticTemplatedCandidate(
            StatisticContext ctx,
            String indicatorCode,
            Map<String, ?> placeholders,
            HintType type,
            int ambiguity,
            int defaultBaseDifficulty,
            BigDecimal defaultQuality,
            String lensName,
            String imageUrl) {
        return buildConfiguredStaticTemplatedCandidate(
                ctx,
                indicatorCode,
                indicatorCode,
                placeholders,
                type,
                ambiguity,
                defaultBaseDifficulty,
                defaultQuality,
                lensName,
                imageUrl
        );
    }

    public static Optional<CandidateHint> buildConfiguredStaticTemplatedCandidate(
            StatisticContext ctx,
            String indicatorCode,
            Map<String, ?> placeholders,
            HintType type,
            int ambiguity,
            int defaultBaseDifficulty,
            BigDecimal defaultQuality,
            String lensName) {
        return buildConfiguredStaticTemplatedCandidate(
                ctx,
                indicatorCode,
                indicatorCode,
                placeholders,
                type,
                ambiguity,
                defaultBaseDifficulty,
                defaultQuality,
                lensName,
                null
        );
    }

    public static CandidateHint buildCandidate(
            String text,
            HintType type,
            Category category,
            DataSource source,
            String sourceIndicator,
            Integer sourceYear,
            int difficulty,
            BigDecimal quality,
            int ambiguity,
            String lensName,
            String imageUrl) {
        return CandidateHint.builder()
                .text(text)
                .type(type)
                .category(category)
                .source(source)
                .sourceIndicator(sourceIndicator)
                .sourceYear(sourceYear)
                .estimatedDifficulty(difficulty)
                .estimatedQuality(quality)
                .ambiguityCount(ambiguity)
                .lensName(lensName)
                .imageUrl(imageUrl)
                .build();
    }
}
