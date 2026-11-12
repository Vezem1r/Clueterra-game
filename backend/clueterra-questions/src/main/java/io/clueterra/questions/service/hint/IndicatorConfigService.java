package io.clueterra.questions.service.hint;

import io.clueterra.questions.repository.IndicatorConfigRepository;
import io.clueterra.questions.repository.IndicatorConfigRecord;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
@RequiredArgsConstructor
public class IndicatorConfigService {

    private final IndicatorConfigRepository repository;
    private final AtomicReference<IndicatorConfigSnapshot> configSnapshot = new AtomicReference<>(IndicatorConfigSnapshot.empty());

    @PostConstruct
    public void init() {
        refresh();
        log.info("IndicatorConfigService initialized");
    }

    @Scheduled(fixedRate = 300_000)
    public void refresh() {
        try {
            Set<String> excludeFromRanking = Set.copyOf(repository.findExcludedFromRanking());
            Set<String> excludeFromPercentile = Set.copyOf(repository.findExcludedFromPercentile());
            Set<String> highProfileIndicators = Set.copyOf(repository.findHighProfileIndicators());
            Set<String> mediumProfileIndicators = Set.copyOf(repository.findMediumProfileIndicators());
            Set<String> lowerIsBetterIndicators = Set.copyOf(repository.findLowerIsBetterIndicators());
            Map<String, String> displayNames = Map.copyOf(repository.findDisplayNames());

            Map<String, IndicatorConfigRecord> newConfigByCode = new HashMap<>();
            Set<String> newHighInterest = new HashSet<>();

            for (IndicatorConfigRecord record : repository.findAll()) {
                newConfigByCode.put(record.indicatorCode(), record);
                if (record.highInterest()) {
                    newHighInterest.add(record.indicatorCode());
                }
            }

            IndicatorConfigSnapshot newSnapshot = new IndicatorConfigSnapshot(
                    excludeFromRanking,
                    excludeFromPercentile,
                    highProfileIndicators,
                    mediumProfileIndicators,
                    lowerIsBetterIndicators,
                    Set.copyOf(newHighInterest),
                    displayNames,
                    Map.copyOf(newConfigByCode)
            );

            configSnapshot.set(newSnapshot);

            log.debug("Refreshed indicator config: {} indicators, {} high interest, {} excluded from ranking",
                    newSnapshot.configByCode().size(),
                    newSnapshot.highInterestIndicators().size(),
                    newSnapshot.excludeFromRanking().size());

        } catch (Exception e) {
            log.warn("Failed to refresh indicator config from DB, keeping existing values", e);
        }
    }

    public boolean isExcludedFromRanking(String indicatorCode) {
        if (indicatorCode == null) return false;
        return configSnapshot.get().excludeFromRanking().contains(indicatorCode);
    }

    public boolean isExcludedFromPercentile(String indicatorCode) {
        if (indicatorCode == null) return false;
        return configSnapshot.get().excludeFromPercentile().contains(indicatorCode);
    }

    public boolean isHighProfile(String indicatorCode) {
        if (indicatorCode == null) return false;
        return configSnapshot.get().highProfileIndicators().contains(indicatorCode);
    }

    public boolean isMediumProfile(String indicatorCode) {
        if (indicatorCode == null) return false;
        return configSnapshot.get().mediumProfileIndicators().contains(indicatorCode);
    }

    public boolean isLowerIsBetter(String indicatorCode) {
        if (indicatorCode == null) return false;
        return configSnapshot.get().lowerIsBetterIndicators().contains(indicatorCode);
    }

    public String getDisplayName(String indicatorCode, String fallback) {
        if (indicatorCode == null) return fallback;
        return configSnapshot.get().displayNames().getOrDefault(indicatorCode, fallback);
    }

    public boolean isHighInterest(String indicatorCode) {
        if (indicatorCode == null) return false;
        return configSnapshot.get().highInterestIndicators().contains(indicatorCode);
    }

    public Optional<IndicatorConfigRecord> getConfig(String indicatorCode) {
        if (indicatorCode == null) return Optional.empty();
        return Optional.ofNullable(configSnapshot.get().configByCode().get(indicatorCode));
    }

    public boolean hasTemplate(String indicatorCode) {
        return getConfig(indicatorCode).map(IndicatorConfigRecord::hasTemplate).orElse(false);
    }

    public String getTemplate(String indicatorCode) {
        return getConfig(indicatorCode).map(IndicatorConfigRecord::hintTemplate).orElse(null);
    }

    public Integer getBaseDifficulty(String indicatorCode, int defaultValue) {
        return getConfig(indicatorCode)
                .map(IndicatorConfigRecord::baseDifficulty)
                .orElse(defaultValue);
    }

    public BigDecimal getQualityScore(String indicatorCode, BigDecimal defaultValue) {
        return getConfig(indicatorCode)
                .map(IndicatorConfigRecord::qualityScore)
                .orElse(defaultValue);
    }

    public String getUnit(String indicatorCode) {
        return getConfig(indicatorCode).map(IndicatorConfigRecord::unit).orElse(null);
    }

}