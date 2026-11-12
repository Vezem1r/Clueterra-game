package io.clueterra.questions.clients.curatedfacts;

import io.clueterra.questions.entity.enums.Category;
import io.clueterra.questions.entity.enums.HintType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@RequiredArgsConstructor
public enum CuratedCountryFactIndicator {

    SIGNATURE_DISH("CURATED_SIGNATURE_DISH", "national-dishes.yml", "Signature dish",
            Category.FOOD, "dish", HintType.CULTURAL, 24, BigDecimal.valueOf(0.88), BigDecimal.valueOf(0.84)),
    SIGNATURE_DRINK("CURATED_SIGNATURE_DRINK", "national-drinks.yml", "Signature drink",
            Category.FOOD, "drink", HintType.CULTURAL, 26, BigDecimal.valueOf(0.86), BigDecimal.valueOf(0.80)),
    EMBLEMATIC_ANIMAL("CURATED_EMBLEMATIC_ANIMAL", "national-animals.yml", "Emblematic animal",
            Category.CULTURE, "animal", HintType.CULTURAL, 30, BigDecimal.valueOf(0.84), BigDecimal.valueOf(0.78)),
    EMBLEMATIC_BIRD("CURATED_EMBLEMATIC_BIRD", "national-birds.yml", "Emblematic bird",
            Category.CULTURE, "bird", HintType.CULTURAL, 32, BigDecimal.valueOf(0.83), BigDecimal.valueOf(0.76)),
    EMBLEMATIC_TREE("CURATED_EMBLEMATIC_TREE", "national-trees.yml", "Emblematic tree",
            Category.NATURE, "tree", HintType.CULTURAL, 36, BigDecimal.valueOf(0.80), BigDecimal.valueOf(0.72)),
    EMBLEMATIC_FLOWER("CURATED_EMBLEMATIC_FLOWER", "national-flowers.yml", "Emblematic flower",
            Category.NATURE, "flower", HintType.CULTURAL, 34, BigDecimal.valueOf(0.81), BigDecimal.valueOf(0.74)),
    TRADITIONAL_DANCE("CURATED_TRADITIONAL_DANCE", "national-dances.yml", "Traditional dance",
            Category.CULTURE, "dance", HintType.CULTURAL, 32, BigDecimal.valueOf(0.84), BigDecimal.valueOf(0.78)),
    TRADITIONAL_INSTRUMENT("CURATED_TRADITIONAL_INSTRUMENT", "national-instruments.yml", "Traditional instrument",
            Category.CULTURE, "instrument", HintType.CULTURAL, 31, BigDecimal.valueOf(0.84), BigDecimal.valueOf(0.78)),
    PROMINENT_SPORT("CURATED_PROMINENT_SPORT", "national-sports.yml", "Culturally prominent sport",
            Category.SPORTS, "sport", HintType.CULTURAL, 24, BigDecimal.valueOf(0.82), BigDecimal.valueOf(0.78)),
    ASSOCIATED_MOTTO("CURATED_ASSOCIATED_MOTTO", "national-mottos.yml", "Associated national motto",
            Category.CULTURE, "motto", HintType.CULTURAL, 35, BigDecimal.valueOf(0.82), BigDecimal.valueOf(0.76)),
    STATEHOOD_DATE("CURATED_STATEHOOD_DATE", "independence-days.yml", "Defining statehood date",
            Category.HISTORY, "date", HintType.HISTORICAL, 37, BigDecimal.valueOf(0.84), BigDecimal.valueOf(0.80));

    private static final Map<String, CuratedCountryFactIndicator> BY_CODE = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(CuratedCountryFactIndicator::getCode, Function.identity()));

    private final String code;
    private final String fileName;
    private final String displayName;
    private final Category category;
    private final String placeholder;
    private final HintType hintType;
    private final int baseDifficulty;
    private final BigDecimal baseQuality;
    private final BigDecimal weight;

    public static Optional<CuratedCountryFactIndicator> byCode(String code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }
}

