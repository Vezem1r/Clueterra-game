package io.clueterra.questions.clients.common;

import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.HintType;
import io.clueterra.questions.service.hint.DynamicDifficultyCalculator;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import io.clueterra.questions.service.hint.lens.HintLens;
import io.clueterra.questions.service.hint.lens.StatisticContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractCachedLens implements HintLens {

    private static final long CACHE_TTL_MS = 600_000;
    private volatile long cacheTimestamp = 0;

    @Override
    public final List<CandidateHint> generate(StatisticContext context) {
        ensureCacheFresh();
        return doGenerate(context);
    }

    protected abstract void loadCache();

    protected abstract List<CandidateHint> doGenerate(StatisticContext context);

    @Override
    public void invalidateCache() {
        cacheTimestamp = 0;
    }

    protected static Optional<CandidateHint> buildTemplatedCandidate(String indicatorCode, Map<String, ?> placeholders, HintType type,
                                                                     Category category, DataSource source, Integer sourceYear, int difficulty, BigDecimal quality,
                                                                     int ambiguity, String lensName, String imageUrl) {
        return TemplatedHintBuilder.buildTemplatedCandidate(indicatorCode, placeholders, type, category, source, sourceYear, difficulty, quality, ambiguity, lensName, imageUrl);
    }

    protected static Optional<CandidateHint> buildDynamicTemplatedCandidate(DynamicDifficultyCalculator difficultyCalculator, String indicatorCode, Map<String, ?> placeholders,
                                                                            HintType type, Category category, DataSource source, Integer sourceYear, int ambiguity,
                                                                            int baseDifficulty, BigDecimal baseQuality, String lensName, String imageUrl) {
        return TemplatedHintBuilder.buildDynamicTemplatedCandidate(difficultyCalculator, indicatorCode, placeholders, type, category, source, sourceYear, ambiguity, baseDifficulty, baseQuality, lensName, imageUrl);
    }

    protected static Optional<CandidateHint> buildDynamicTemplatedCandidate(DynamicDifficultyCalculator difficultyCalculator, String indicatorCode, Map<String, ?> placeholders,
                                                                            HintType type, Category category, DataSource source, Integer sourceYear,
                                                                            int ambiguity, int baseDifficulty, BigDecimal baseQuality, String lensName) {
        return buildDynamicTemplatedCandidate(difficultyCalculator, indicatorCode, placeholders, type, category, source, sourceYear, ambiguity, baseDifficulty, baseQuality, lensName, null);
    }

    protected static String pickVariantCode(Map<String, List<String>> variantGroups,
                                            String baseCode, String countryCode) {
        List<String> suffixes = variantGroups.get(baseCode);
        if (suffixes != null && !suffixes.isEmpty()) {
            int hash = (countryCode + baseCode).hashCode() & 0x7FFFFFFF;
            return baseCode + suffixes.get(hash % suffixes.size());
        }
        return baseCode;
    }

    private void ensureCacheFresh() {
        long now = System.currentTimeMillis();
        if (now - cacheTimestamp > CACHE_TTL_MS) {
            synchronized (this) {
                now = System.currentTimeMillis();
                if (now - cacheTimestamp > CACHE_TTL_MS) {
                    loadCache();
                    cacheTimestamp = System.currentTimeMillis();
                }
            }
        }
    }
}
