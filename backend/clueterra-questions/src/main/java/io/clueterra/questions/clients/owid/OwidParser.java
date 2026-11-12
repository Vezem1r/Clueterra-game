package io.clueterra.questions.clients.owid;

import java.time.Clock;
import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.clients.owid.dto.OwidDataset;
import io.clueterra.questions.clients.owid.dto.OwidLatestRow;
import io.clueterra.questions.config.DynamicMappingsConfig;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.service.common.CountryCodeCache;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OwidParser {

    private final CountryCodeCache countryCodeCache;
    private final DynamicMappingsConfig mappingsConfig;
    private final Clock clock;

    private Map<String, String> staticIso3ToIso2 = Map.of();

    @PostConstruct
    public void init() {
        Map<String, String> reversed = new HashMap<>();
        mappingsConfig.getCountryIso3().forEach((iso2, iso3) -> reversed.put(iso3.toUpperCase(), iso2.toUpperCase()));
        staticIso3ToIso2 = Map.copyOf(reversed);
        log.info("[OWID] Static ISO3->ISO2 map loaded: {} entries", staticIso3ToIso2.size());
    }

    public List<RawCountryData> parseGrapherCsv(String csv, OwidDataset dataset) {
        String[] lines = csv.split("\n");
        if (lines.length < 2) return List.of();

        String[] headers = splitCsvLine(lines[0]);
        int codeIdx = indexOf(headers, "Code");
        int yearIdx = indexOf(headers, "Year");
        int valueIdx = indexOf(headers, dataset.csvColumn());

        if (codeIdx < 0 || yearIdx < 0 || valueIdx < 0) {
            log.warn(
                    "[OWID] CSV headers not recognized for dataset {}, looking for column '{}'",
                    dataset.name(),
                    dataset.csvColumn());
            log.debug("[OWID] Available headers: {}", String.join(", ", headers));
            return List.of();
        }

        Map<String, OwidLatestRow> latestByIso3 = new HashMap<>();
        int maxIdx = Math.max(Math.max(codeIdx, yearIdx), valueIdx);
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            String[] cols = splitCsvLine(line);
            if (!line.isEmpty() && cols.length > maxIdx) {
                String iso3 = clean(cols[codeIdx]);
                String yearStr = clean(cols[yearIdx]);
                String valStr = clean(cols[valueIdx]);
                if (iso3.length() == 3 && !valStr.isEmpty()) {
                    OwidLatestRow existing = latestByIso3.get(iso3);
                    if (existing == null || yearStr.compareTo(existing.year()) > 0) {
                        latestByIso3.put(iso3, new OwidLatestRow(yearStr, valStr));
                    }
                }
            }
        }

        int currentYear = LocalDate.now(clock).getYear();
        List<RawCountryData> result = new ArrayList<>();
        int unmapped = 0;

        for (Map.Entry<String, OwidLatestRow> entry : latestByIso3.entrySet()) {
            Optional<String> iso2Opt = countryCodeCache.iso3ToIso2(entry.getKey());
            if (iso2Opt.isEmpty()) {
                iso2Opt = Optional.ofNullable(staticIso3ToIso2.get(entry.getKey().toUpperCase()));
            }
            if (iso2Opt.isPresent()) {
                String iso2 = iso2Opt.get();
                OwidLatestRow row = entry.getValue();
                try {
                    BigDecimal value = new BigDecimal(row.value()).setScale(4, RoundingMode.HALF_UP);
                    if (value.compareTo(BigDecimal.ZERO) >= 0) {
                        int year = parseYear(row.year(), currentYear);
                        RawStatistic stat = new RawStatistic(
                                dataset.indicatorCode(),
                                dataset.displayName(),
                                dataset.category(),
                                value,
                                value.stripTrailingZeros().toPlainString(),
                                dataset.unit(),
                                year,
                                BigDecimal.valueOf(dataset.confidence()),
                                BigDecimal.valueOf(dataset.weight()));
                        result.add(RawCountryData.builder()
                                .isoAlpha2(iso2)
                                .nameEn(iso2)
                                .source(DataSource.OUR_WORLD_IN_DATA)
                                .statistics(List.of(stat))
                                .build());
                    }
                } catch (NumberFormatException e) {
                    log.trace("[OWID] Cannot parse '{}' for {}/{}", row.value(), dataset.indicatorCode(), entry.getKey());
                }
            } else {
                unmapped++;
            }
        }

        if (unmapped > 0) {
            log.warn("[OWID] {}: {} ISO3 codes had no ISO2 mapping", dataset.indicatorCode(), unmapped);
        }
        log.info("[OWID] Parsed {} countries for {}", result.size(), dataset.indicatorCode());
        return result;
    }

    private int parseYear(String yearStr, int fallback) {
        try {
            return Integer.parseInt(yearStr);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String[] splitCsvLine(String line) {
        return line.split(",", -1);
    }

    private String clean(String s) {
        return s == null ? "" : s.trim().replace("\"", "");
    }

    private int indexOf(String[] headers, String name) {
        String normalized = name.toLowerCase().trim();
        for (int i = 0; i < headers.length; i++) {
            if (clean(headers[i]).toLowerCase().equals(normalized)) return i;
        }
        return -1;
    }
}
