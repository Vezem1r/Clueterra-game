package io.clueterra.questions.clients.brands.hintgeneration;

import io.clueterra.questions.clients.brands.dto.BrandConstants;
import io.clueterra.questions.clients.brands.dto.BrandRow;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class BrandAmbiguityCalculator {

    public int compute(String indicatorCode, String countryCode,
                       Map<String, List<BrandRow>> allByCountry,
                       String primaryBrand) {
        if (primaryBrand != null && !primaryBrand.isBlank()) {
            return countCountriesSharingBrand(allByCountry, countryCode, primaryBrand);
        }

        return switch (indicatorCode) {
            case "BRAND_NO_GLOBAL" -> countWithFewBrands(allByCountry);
            case "BRAND_SINGLE_ICON" -> countWithExactlyOneBrand(allByCountry);
            case "BRAND_STARTUP_NATION" -> countSmallWithTech(allByCountry);
            default -> 20;
        };
    }

    private int countCountriesSharingBrand(Map<String, List<BrandRow>> allByCountry,
                                           String countryCode, String brandName) {
        return (int) allByCountry.entrySet().stream()
                .filter(e -> !e.getKey().equals(countryCode))
                .filter(e -> e.getValue().stream()
                        .anyMatch(r -> r.brandName().equalsIgnoreCase(brandName)))
                .count();
    }

    private int countSmallWithTech(Map<String, List<BrandRow>> allByCountry) {
        return (int) allByCountry.entrySet().stream()
                .filter(e -> e.getValue().size() <= 5
                        && e.getValue().stream()
                        .anyMatch(r -> BrandConstants.TECH_INDUSTRIES.contains(r.industry())))
                .count();
    }

    private int countWithFewBrands(Map<String, List<BrandRow>> allByCountry) {
        return (int) allByCountry.values().stream()
                .filter(rows -> rows.size() <= 1)
                .count();
    }

    private int countWithExactlyOneBrand(Map<String, List<BrandRow>> allByCountry) {
        return (int) allByCountry.values().stream()
                .filter(rows -> rows.size() == 1
                        && rows.getFirst().globalRecognition() >= BrandConstants.HIGH_RECOGNITION_THRESHOLD)
                .count();
    }
}
