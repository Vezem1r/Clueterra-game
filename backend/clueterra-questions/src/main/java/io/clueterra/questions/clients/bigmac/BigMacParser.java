package io.clueterra.questions.clients.bigmac;

import java.time.Clock;
import io.clueterra.questions.clients.common.dto.RawCountryData;
import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.config.DynamicMappingsConfig;
import io.clueterra.questions.entity.enums.Category;
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
public class BigMacParser {

    private final CountryCodeCache countryCodeCache;
    private final DynamicMappingsConfig mappingsConfig;
    private final Clock clock;

    private Map<String, String> staticIso3ToIso2 = Map.of();

    @PostConstruct
    public void init() {
        Map<String, String> reversed = new HashMap<>();
        mappingsConfig.getCountryIso3()
                .forEach((iso2, iso3) -> reversed.put(iso3.toUpperCase(), iso2.toUpperCase()));
        staticIso3ToIso2 = Map.copyOf(reversed);
        log.info("[BIG_MAC_INDEX] Static ISO3->ISO2 map loaded: {} entries", staticIso3ToIso2.size());
    }

    public List<RawCountryData> parseCsv(String csv) {
        String[] lines = csv.split("\n");
        if (lines.length < 2) return List.of();

        String[] headers = lines[0].trim().split(",");
        int dateIdx = indexOf(headers, "date");
        int isoIdx = indexOf(headers, "iso_a3");
        int dollarPriceIdx = indexOf(headers, "dollar_price");
        int usdAdjIdx = indexOf(headers, "USD_adjusted");
        int adjPriceIdx = indexOf(headers, "adj_price");

        if (dateIdx < 0 || isoIdx < 0 || dollarPriceIdx < 0) {
            log.warn("[BIG_MAC_INDEX] CSV headers not recognized");
            return List.of();
        }

        int valuationIdx = usdAdjIdx >= 0 ? usdAdjIdx : adjPriceIdx;

        Map<String, String[]> latestByIso3 = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            String[] cols = line.split(",", -1);
            if (!line.isEmpty() && cols.length > Math.max(isoIdx, dollarPriceIdx)) {
                String iso3 = cols[isoIdx].trim().replace("\"", "");
                String date = cols[dateIdx].trim().replace("\"", "");
                String[] existing = latestByIso3.get(iso3);
                if (existing == null || date.compareTo(existing[dateIdx].trim().replace("\"", "")) > 0) {
                    latestByIso3.put(iso3, cols);
                }
            }
        }

        int currentYear = LocalDate.now(clock).getYear();
        List<RawCountryData> result = new ArrayList<>();
        int unmapped = 0;

        for (Map.Entry<String, String[]> entry : latestByIso3.entrySet()) {
            Optional<String> iso2Opt = countryCodeCache.iso3ToIso2(entry.getKey());
            if (iso2Opt.isEmpty()) {
                iso2Opt = Optional.ofNullable(staticIso3ToIso2.get(entry.getKey().toUpperCase()));
            }
            if (iso2Opt.isEmpty()) {
                unmapped++;
                continue;
            }

            String iso2 = iso2Opt.get();
            String[] cols = entry.getValue();
            List<RawStatistic> stats = new ArrayList<>();

            parsePrice(cols, dollarPriceIdx, currentYear, stats);
            parseValuation(cols, valuationIdx, currentYear, stats);

            if (!stats.isEmpty()) {
                result.add(RawCountryData.builder()
                        .isoAlpha2(iso2)
                        .nameEn(iso2)
                        .source(DataSource.BIG_MAC_INDEX)
                        .statistics(stats)
                        .build());
            }
        }

        if (unmapped > 0) {
            log.warn("[BIG_MAC_INDEX] {} ISO3 codes had no ISO2 mapping", unmapped);
        }
        log.info("[BIG_MAC_INDEX] Parsed {} countries from Big Mac Index CSV", result.size());
        return result;
    }

    private void parsePrice(String[] cols, int dollarPriceIdx, int currentYear, List<RawStatistic> stats) {
        try {
            String priceStr = cols[dollarPriceIdx].trim().replace("\"", "");
            if (priceStr.isEmpty() || "NA".equals(priceStr)) return;
            BigDecimal price = new BigDecimal(priceStr).setScale(2, RoundingMode.HALF_UP);
            String priceText = price.toPlainString();
            stats.add(new RawStatistic(BigMacConstants.PRICE_INDICATOR, BigMacConstants.PRICE_DISPLAY_NAME,
                    Category.ECONOMY, price, priceText, "USD", currentYear,
                    BigDecimal.valueOf(0.70), BigDecimal.valueOf(0.65)));
        } catch (NumberFormatException e) {
            log.debug("[BIG_MAC_INDEX] Could not parse dollar_price: {}", e.getMessage());
        }
    }

    private void parseValuation(String[] cols, int valuationIdx, int currentYear, List<RawStatistic> stats) {
        if (valuationIdx < 0 || cols.length <= valuationIdx) return;
        try {
            String valStr = cols[valuationIdx].trim().replace("\"", "");
            if (valStr.isEmpty() || "NA".equals(valStr)) return;
            BigDecimal valuation = new BigDecimal(valStr)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP);
            String sign = valuation.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
            String valuationText = sign + valuation.toPlainString() + "%";
            stats.add(new RawStatistic(BigMacConstants.VALUATION_INDICATOR, BigMacConstants.VALUATION_DISPLAY_NAME,
                    Category.ECONOMY, valuation, valuationText, "%", currentYear,
                    BigDecimal.valueOf(0.70), BigDecimal.valueOf(0.65)));
        } catch (NumberFormatException e) {
            log.debug("[BIG_MAC_INDEX] Could not parse valuation: {}", e.getMessage());
        }
    }

    private int indexOf(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().replace("\"", "").equalsIgnoreCase(name)) return i;
        }
        return -1;
    }
}
