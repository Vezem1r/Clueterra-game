package io.clueterra.questions.clients.nagerdate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.entity.enums.Category;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class NagerDateParser {

    private final ObjectMapper objectMapper;

    private static final BigDecimal CONFIDENCE = BigDecimal.valueOf(0.90);
    private static final int MAX_NAMES = 12;

    private static final Set<String> GLOBALLY_COMMON = Set.of(
            "New Year's Day", "New Year's Eve", "Christmas Day", "Christmas Eve",
            "Good Friday", "Easter Sunday", "Easter Monday", "Labour Day", "Labor Day",
            "International Workers' Day", "May Day", "All Saints' Day", "All Souls' Day",
            "Immaculate Conception Day", "Ascension Day", "Corpus Christi", "Epiphany",
            "King's Day", "Remembrance Day", "Presidents' Day", "Christmas Day (Orthodox)",
            "May Day / International Workers' Day"
    );

    public List<RawStatistic> parseHolidays(String body, String countryCode, int year) {
        List<RawStatistic> stats = new ArrayList<>();
        try {
            JsonNode arr = objectMapper.readTree(body);
            if (!arr.isArray() || arr.isEmpty()) return stats;

            List<String> localNames = new ArrayList<>();
            List<String> englishNames = new ArrayList<>();
            int globalCount = 0;

            for (JsonNode holiday : arr) {
                if (!holiday.path("global").asBoolean(false)) continue;
                globalCount++;

                String name = holiday.path("name").asText("");
                String localName = holiday.path("localName").asText(name);

                if (!localName.isBlank()) {
                    localNames.add(localName);
                }
                if (!name.isBlank()) {
                    englishNames.add(name);
                }
            }

            if (globalCount > 0) {
                stats.add(new RawStatistic("HOLIDAY_COUNT", "public holidays", Category.CULTURE,
                        BigDecimal.valueOf(globalCount), null, "days", year, CONFIDENCE, BigDecimal.valueOf(0.55)));
            }

            List<String> dedupedLocal = localNames.stream().distinct().limit(6).toList();
            if (!dedupedLocal.isEmpty()) {
                stats.add(new RawStatistic("HOLIDAY_NOTABLE", "notable local-language holidays", Category.CULTURE,
                        null, String.join(", ", dedupedLocal), null, year, CONFIDENCE, BigDecimal.valueOf(0.65)));
            }

            List<String> dedupedEn = englishNames.stream()
                    .distinct()
                    .filter(n -> GLOBALLY_COMMON.stream().noneMatch(common -> common.equalsIgnoreCase(n.trim())))
                    .limit(MAX_NAMES)
                    .toList();
            if (!dedupedEn.isEmpty()) {
                stats.add(new RawStatistic("HOLIDAY_UNIQUE_EN", "holiday english names", Category.CULTURE,
                        null, String.join(", ", dedupedEn), null, year, CONFIDENCE, BigDecimal.valueOf(0.80)));
            }

        } catch (Exception e) {
            log.warn("[NAGER_DATE] Failed to parse holidays for {}", countryCode, e);
        }
        return stats;
    }
}
