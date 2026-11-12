package io.clueterra.questions.clients.brands;

import java.time.Clock;
import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.clients.brands.dto.BrandRow;
import io.clueterra.questions.clients.brands.repository.BrandRepository;
import io.clueterra.questions.clients.common.AbstractStagingEnricher;
import io.clueterra.questions.clients.common.StagingRepository;
import io.clueterra.questions.entity.enums.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BrandEnricher extends AbstractStagingEnricher<BrandRow> {

    private static final BigDecimal CONFIDENCE = BigDecimal.valueOf(0.95);

    private final BrandRepository repository;
    private final Clock clock;

    @Override
    protected StagingRepository<BrandRow> getRepository() {
        return repository;
    }

    @Override
    protected String logPrefix() {
        return "BRANDS";
    }

    @Override
    protected List<RawStatistic> enrichRows(String countryCode, List<BrandRow> rows) {
        int currentYear = LocalDate.now(clock).getYear();
        List<RawStatistic> stats = new ArrayList<>();

        stats.add(new RawStatistic(
                "BRAND_COUNT", "global brands headquartered",
                Category.CULTURE, BigDecimal.valueOf(rows.size()), null, "count",
                currentYear, CONFIDENCE, BigDecimal.valueOf(0.70)
        ));

        double avgRec = rows.stream()
                .mapToInt(BrandRow::globalRecognition)
                .average().orElse(5.0);
        stats.add(new RawStatistic(
                "BRAND_AVG_RECOGNITION", "average brand recognition",
                Category.CULTURE, BigDecimal.valueOf(avgRec).setScale(1, RoundingMode.HALF_UP),
                null, "score (1-10)",
                currentYear, CONFIDENCE, BigDecimal.valueOf(0.60)
        ));

        long distinctIndustries = rows.stream()
                .map(BrandRow::industry)
                .distinct().count();
        stats.add(new RawStatistic(
                "BRAND_INDUSTRY_DIVERSITY", "brand industry diversity",
                Category.CULTURE, BigDecimal.valueOf(distinctIndustries), null, "count",
                currentYear, CONFIDENCE, BigDecimal.valueOf(0.55)
        ));

        rows.stream()
                .filter(r -> r.foundedYear() != null)
                .min(Comparator.comparingInt(BrandRow::foundedYear))
                .ifPresent(oldest -> stats.add(new RawStatistic(
                        "BRAND_OLDEST_YEAR", "oldest active global brand",
                        Category.CULTURE, BigDecimal.valueOf(oldest.foundedYear()), null, "year",
                        currentYear, CONFIDENCE, BigDecimal.valueOf(0.50)
                )));

        List<BrandRow> topBrands = rows.stream()
                .sorted(Comparator.comparingInt(BrandRow::globalRecognition).reversed())
                .limit(3)
                .toList();
        for (int i = 0; i < topBrands.size(); i++) {
            BrandRow brand = topBrands.get(i);
            String valueText = String.join("|",
                    brand.brandName(),
                    brand.industry(),
                    String.valueOf(brand.foundedYear()));

            stats.add(new RawStatistic(
                    "BRAND_ICONIC_" + (i + 1), "iconic global brand",
                    Category.CULTURE, null, valueText,
                    null, currentYear, CONFIDENCE, BigDecimal.valueOf(0.75)
            ));
        }

        return stats;
    }
}
