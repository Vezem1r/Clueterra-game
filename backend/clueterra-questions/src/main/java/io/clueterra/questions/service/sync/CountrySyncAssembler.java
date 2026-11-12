package io.clueterra.questions.service.sync;

import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.service.source.SourceStatisticProcessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class CountrySyncAssembler implements SourceStatisticProcessor {

    public Optional<Country> mergeCountryData(List<RawCountryData> rawDataList) {
        if (rawDataList == null || rawDataList.isEmpty()) {
            return Optional.empty();
        }

        List<RawCountryData> sorted = rawDataList.stream()
                .filter(RawCountryData::isValid)
                .sorted(Comparator.comparingInt(d -> d.getSource().getPriority()))
                .toList();

        if (sorted.isEmpty()) {
            return Optional.empty();
        }

        String isoAlpha2 = null;
        String isoAlpha3 = null;
        String nameEn = null;
        String nameNative = null;
        String region = null;
        String subregion = null;
        String capital = null;
        String flagEmoji = null;
        String flagUrl = null;

        for (RawCountryData data : sorted) {
            if (isoAlpha2 == null) isoAlpha2 = data.getIsoAlpha2();
            if (isoAlpha3 == null) isoAlpha3 = data.getIsoAlpha3();
            if (nameEn == null) nameEn = normalizeText(data.getNameEn());
            if (nameNative == null) nameNative = data.getNameNative().map(this::normalizeText).orElse(null);
            if (region == null) region = data.getRegion().map(this::normalizeText).orElse(null);
            if (subregion == null) subregion = data.getSubregion().map(this::normalizeText).orElse(null);
            if (capital == null) capital = data.getCapital().map(this::normalizeText).orElse(null);
            if (flagEmoji == null) flagEmoji = data.getFlagEmoji().orElse(null);
            if (flagUrl == null) flagUrl = data.getFlagUrl().orElse(null);
        }

        Country country = Country.builder()
                .isoAlpha2(isoAlpha2)
                .isoAlpha3(isoAlpha3)
                .nameEn(nameEn)
                .nameNative(nameNative)
                .region(region)
                .subregion(subregion)
                .capital(capital)
                .flagEmoji(flagEmoji)
                .flagUrl(flagUrl)
                .isActive(true)
                .build();

        log.debug("Assembled country snapshot: {} ({}) from {} sources", isoAlpha2, isoAlpha3, rawDataList.size());
        return Optional.of(country);
    }

    public List<CountryStatistic> buildStatisticsSnapshot(Long countryId, List<RawCountryData> rawDataList) {
        Map<String, List<StatisticWithSource>> byIndicator = new HashMap<>();

        for (RawCountryData data : rawDataList) {
            for (RawStatistic stat : data.getStatistics()) {
                byIndicator.computeIfAbsent(stat.indicatorCode(), k -> new ArrayList<>())
                        .add(new StatisticWithSource(stat, data.getSource(), data.getFetchedAt()));
            }
        }

        List<CountryStatistic> result = new ArrayList<>();

        for (Map.Entry<String, List<StatisticWithSource>> entry : byIndicator.entrySet()) {
            List<StatisticWithSource> candidates = entry.getValue();
            candidates.sort(Comparator
                    .comparingInt((StatisticWithSource s) -> s.source().getPriority())
                    .thenComparing((StatisticWithSource s) -> s.stat().year() != null ? s.stat().year() : 0,
                            Comparator.reverseOrder())
                    .thenComparing((StatisticWithSource s) -> s.stat().getConfidenceOrDefault(),
                            Comparator.reverseOrder()));

            StatisticWithSource best = candidates.getFirst();
            result.add(toCountryStatistic(countryId, best.source(), best.fetchedAt(), best.stat(), false));
        }

        log.debug("Assembled {} statistics for country {}", result.size(), countryId);
        return result;
    }

    public List<CountryStatistic> buildDynamicStatistics(Long countryId,
                                                         DataSource source,
                                                         Instant fetchedAt,
                                                         List<RawStatistic> rawStatistics) {
        if (rawStatistics == null || rawStatistics.isEmpty()) {
            return List.of();
        }

        return rawStatistics.stream()
                .map(raw -> toCountryStatistic(countryId, source, fetchedAt, raw, true))
                .toList();
    }

    private CountryStatistic toCountryStatistic(Long countryId,
                                                DataSource source,
                                                Instant fetchedAt,
                                                RawStatistic stat,
                                                boolean isDynamic) {
        return CountryStatistic.builder()
                .countryId(countryId)
                .source(source)
                .indicatorCode(stat.indicatorCode())
                .indicatorName(normalizeText(stat.indicatorName()))
                .category(stat.category())
                .value(stat.hasValue() ? normalizeNumericValue(stat.value()) : null)
                .valueText(normalizeText(stat.valueText()))
                .unit(normalizeUnit(stat.unit()))
                .year(stat.year())
                .imageUrl(stat.imageUrl())
                .originalImageUrl(stat.originalImageUrl())
                .imageSourcePageUrl(stat.imageSourcePageUrl())
                .imageAuthorName(stat.imageAuthorName())
                .imageAttributionText(stat.imageAttributionText())
                .imageLicenseLabel(stat.imageLicenseLabel())
                .imageLicenseUrl(stat.imageLicenseUrl())
                .confidence(stat.getConfidenceOrDefault())
                .weight(stat.getWeightOrDefault())
                .isCurrent(true)
                .isDynamic(isDynamic)
                .fetchedAt(fetchedAt)
                .effectiveDate(null)
                .build();
    }

    private BigDecimal normalizeNumericValue(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return smartRound(value);
    }

    private BigDecimal smartRound(BigDecimal value) {
        BigDecimal abs = value.abs();

        if (abs.compareTo(BigDecimal.valueOf(1_000_000_000)) >= 0) {
            return value.setScale(-6, RoundingMode.HALF_UP);
        }

        if (abs.compareTo(BigDecimal.valueOf(1_000_000)) >= 0) {
            return value.setScale(-3, RoundingMode.HALF_UP);
        }

        if (abs.compareTo(BigDecimal.valueOf(1000)) >= 0) {
            return value.setScale(2, RoundingMode.HALF_UP);
        }

        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private String normalizeText(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }

        String normalized = StringEscapeUtils.unescapeHtml4(text);
        normalized = normalized.replace('\u00A0', ' ');
        normalized = normalized.trim().replaceAll("\\s+", " ");
        normalized = normalized
                .replace("(estimate)", "")
                .replace("(estimated)", "")
                .replace("[provisional]", "")
                .replace("(provisional)", "")
                .trim();

        return StringUtils.isBlank(normalized) ? null : normalized;
    }

    private String normalizeUnit(String unit) {
        return StringUtils.isBlank(unit) ? null : unit.trim();
    }

}
