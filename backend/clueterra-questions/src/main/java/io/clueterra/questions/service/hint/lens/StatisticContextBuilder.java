package io.clueterra.questions.service.hint.lens;

import io.clueterra.questions.entity.Country;
import io.clueterra.questions.entity.CountryStatistic;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.repository.CountryRepository;
import io.clueterra.questions.repository.StatisticRepository;
import io.clueterra.questions.repository.RegionalRankInfo;
import io.clueterra.questions.service.hint.IndicatorConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class StatisticContextBuilder {

    private final StatisticRepository statisticRepository;
    private final CountryRepository countryRepository;
    private final IndicatorConfigService indicatorConfigService;

    private final Map<String, IndicatorMetadata> metadataCache = new ConcurrentHashMap<>();
    private Map<Long, String> countryNameCache = new ConcurrentHashMap<>();
    private int totalActiveCountries = 0;

    private static final Set<String> LOWER_IS_BETTER_KEYWORDS = Set.of(
            "unemployment", "mortality", "poverty", "inflation", "death",
            "corruption", "crime", "pollution", "illiteracy", "debt",
            "pm2.5", "co2", "infant_mortality"
    );

    @PostConstruct
    public void init() {
        totalActiveCountries = (int) countryRepository.countByIsActiveTrue();
        if (totalActiveCountries == 0) {
            totalActiveCountries = 195;
        }
        countryNameCache = new ConcurrentHashMap<>(countryRepository.findAllCountryNames());
    }

    public StatisticContext build(Country country, CountryStatistic statistic) {
        String code = statistic.getIndicatorCode();
        IndicatorMetadata metadata = getMetadata(code, statistic.getIndicatorName());

        StatisticContextDraft builder = StatisticContext.builder()
                .country(country)
                .statistic(statistic)
                .totalCountriesWithData(metadata.countryCount())
                .totalActiveCountries(totalActiveCountries)
                .indicatorDisplayName(getDisplayName(code, statistic.getIndicatorName()))
                .isHigherBetter(isHigherBetter(code, statistic.getIndicatorName()));

        Optional<CountryStatistic> topRanked = statisticRepository.findTopRanked(code);
        topRanked.ifPresent(top -> {
            builder.topValue(top.getValue());
            loadCountryName(top.getCountryId()).ifPresent(builder::topCountryName);
        });

        List<CountryStatistic> bottom = statisticRepository.findBottomN(code, 1);
        if (!bottom.isEmpty()) {
            CountryStatistic bottomStat = bottom.getFirst();
            builder.bottomValue(bottomStat.getValue());
            loadCountryName(bottomStat.getCountryId()).ifPresent(builder::bottomCountryName);
        }

        if (country.getRegion() != null) {
            List<RegionalRankInfo> regionalRanks = statisticRepository
                    .findRegionalRankings(code, country.getRegion());
            if (!regionalRanks.isEmpty()) {
                RegionalRankInfo topRegional = regionalRanks.getFirst();
                builder.regionalTopCountryName(topRegional.countryName());
                builder.regionalTopRank(topRegional.rank());
            }
        }

        return builder.build();
    }

    public StatisticContext buildMinimal(Country country, CountryStatistic statistic) {
        String code = statistic.getIndicatorCode();
        if (statistic.getSource() == DataSource.BUDGET_PROJECTS) {
            return StatisticContext.builder()
                    .country(country)
                    .statistic(statistic)
                    .totalActiveCountries(totalActiveCountries)
                    .indicatorDisplayName(statistic.getIndicatorName())
                    .build();
        }
        IndicatorMetadata metadata = getMetadata(code, statistic.getIndicatorName());

        return StatisticContext.builder()
                .country(country)
                .statistic(statistic)
                .totalCountriesWithData(metadata.countryCount())
                .totalActiveCountries(totalActiveCountries)
                .indicatorDisplayName(getDisplayName(code, statistic.getIndicatorName()))
                .isHigherBetter(isHigherBetter(code, statistic.getIndicatorName()))
                .build();
    }

    public void refreshCache() {
        totalActiveCountries = (int) countryRepository.countByIsActiveTrue();
        countryNameCache = new ConcurrentHashMap<>(countryRepository.findAllCountryNames());
        metadataCache.clear();
    }

    public void invalidateIndicatorCodes(Set<String> indicatorCodes) {
        if (indicatorCodes == null || indicatorCodes.isEmpty()) {
            return;
        }
        indicatorCodes.forEach(metadataCache::remove);
    }

    private IndicatorMetadata getMetadata(String code, String name) {
        if (code == null || code.isBlank()) {
            return new IndicatorMetadata(code, name, 0, null, null, null);
        }
        return metadataCache.computeIfAbsent(code, key -> loadMetadata(key, name));
    }

    private IndicatorMetadata loadMetadata(String code, String name) {
        int count = statisticRepository.countCountriesWithIndicator(code);
        return new IndicatorMetadata(code, name, count, null, null, null);
    }

    private Optional<String> loadCountryName(Long countryId) {
        if (countryId == null) return Optional.empty();
        String cached = countryNameCache.get(countryId);
        if (cached != null) return Optional.of(cached);
        Optional<String> name = countryRepository.findNameById(countryId);
        name.ifPresent(n -> countryNameCache.put(countryId, n));
        return name;
    }

    private String getDisplayName(String code, String indicatorName) {
        String mapped = indicatorConfigService.getDisplayName(code, null);
        if (mapped != null) return mapped;

        if (indicatorName != null && !indicatorName.isBlank()) {
            return cleanIndicatorName(indicatorName);
        }

        return cleanIndicatorCode(code);
    }

    private String cleanIndicatorName(String name) {
        return name
                .replace("(current US$)", "")
                .replace("(% of GDP)", "")
                .replace("(% of total)", "")
                .replace(", total", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
    }

    private String cleanIndicatorCode(String code) {
        if (code == null) return "unknown";

        String cleaned = code
                .replaceFirst("^(CIA_|GEO_|CURATED_|WIKIDATA_|OSM_|WB_)", "")
                .toLowerCase()
                .replace("_", " ")
                .replace(".", " ");

        return cleaned.trim();
    }

    private boolean isHigherBetter(String code, String indicatorName) {
        if (code == null) return true;

        if (indicatorConfigService.isLowerIsBetter(code)) {
            return false;
        }

        String lower = code.toLowerCase();
        String nameLower = indicatorName != null ? indicatorName.toLowerCase() : "";

        for (String negative : LOWER_IS_BETTER_KEYWORDS) {
            if (lower.contains(negative) || nameLower.contains(negative)) {
                return false;
            }
        }

        if (lower.contains("pm25") || lower.contains("pm2.5")) return false;
        return !lower.contains("co2") && !lower.contains("emission");
    }

}
