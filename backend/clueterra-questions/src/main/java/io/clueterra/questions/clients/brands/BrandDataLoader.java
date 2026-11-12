package io.clueterra.questions.clients.brands;

import java.time.Clock;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.clueterra.questions.clients.brands.dto.BrandRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrandDataLoader {

    private final Clock clock;

    @SuppressWarnings("unchecked")
    public List<BrandRow> loadBrands() {
        try (InputStream is = new ClassPathResource("mappings/global-brands.yml").getInputStream()) {
            ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
            Map<String, Object> data = yaml.readValue(is, Map.class);
            List<Map<String, Object>> rawBrands = (List<Map<String, Object>>) data.get("brands");

            if (rawBrands == null || rawBrands.isEmpty()) {
                log.warn("[BRANDS] No brands found in global-brands.yml");
                return List.of();
            }

            List<BrandRow> allBrands = rawBrands.stream()
                    .map(this::parseBrand)
                    .toList();

            Map<String, List<BrandRow>> byCountry = allBrands.stream()
                    .collect(Collectors.groupingBy(BrandRow::countryCode));

            List<BrandRow> ranked = new ArrayList<>();
            for (Map.Entry<String, List<BrandRow>> entry : byCountry.entrySet()) {
                AtomicInteger rank = new AtomicInteger(1);
                entry.getValue().stream()
                        .sorted(Comparator.comparingInt(BrandRow::globalRecognition).reversed())
                        .forEach(b -> ranked.add(new BrandRow(
                                b.countryCode(), b.brandName(), b.industry(),
                                b.foundedYear(), b.globalRecognition(),
                                rank.getAndIncrement(), true, Instant.now(clock)
                        )));
            }

            log.info("[BRANDS] Loaded {} brands from YAML for {} countries", ranked.size(), byCountry.size());
            return ranked;
        } catch (Exception e) {
            log.error("[BRANDS] Failed to load global-brands.yml", e);
            return List.of();
        }
    }

    private BrandRow parseBrand(Map<String, Object> raw) {
        String name = (String) raw.get("name");
        String industry = (String) raw.get("industry");
        String country = raw.get("country") != null ? raw.get("country").toString() : "XX";

        if (!StringUtils.hasText(name)) name = "Unknown Brand";
        if (!StringUtils.hasText(industry)) industry = "other";

        Integer founded = raw.get("founded") != null ? ((Number) raw.get("founded")).intValue() : null;
        int recognition = raw.get("recognition") != null ? ((Number) raw.get("recognition")).intValue() : 5;

        return new BrandRow(country, name, industry, founded, recognition, 1, true, Instant.now(clock));
    }
}
