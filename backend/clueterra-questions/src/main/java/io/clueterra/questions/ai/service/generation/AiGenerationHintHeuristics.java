package io.clueterra.questions.ai.service.generation;

import io.clueterra.questions.entity.Country;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class AiGenerationHintHeuristics {

    private static final int EXPERT_DIFFICULTY_FLOOR = 85;
    private static final int EXPERT_DIFFICULTY_OFFSET = 30;
    private static final int EXPERT_MIN_BATCH_SIZE = 6;
    private static final int EXPERT_OBSCURE_COUNTRY_FLOOR = 60;

    private static final Set<String> STOP_WORDS = Set.of(
            "this", "that", "here", "there", "their", "about", "after", "before", "under", "over",
            "from", "with", "into", "around", "which", "whose", "while", "where", "when", "because",
            "country", "territory", "nation", "region", "people", "years", "year", "world", "global",
            "official", "annual", "average", "main", "major", "located", "uses", "would", "could",
            "therefore", "roughly", "nearly", "almost", "entirely", "across", "these", "those",
            "island", "islands", "archipelago", "territories", "residents", "population", "economy"
    );

    public boolean isLowSignalHint(String hintText) {
        String normalized = normalize(hintText);
        if (normalized == null) {
            return true;
        }
        return containsAny(normalized,
                "capital city",
                "seat of government",
                "official currency",
                "uses the euro",
                "currency code",
                "phone prefix",
                "dialing",
                "calling code",
                "driving side",
                "time zone",
                "timezone",
                "language code",
                "top level domain",
                "tld",
                "flag is your primary clue",
                "flag; its unique colors",
                "voting starts at",
                "voting age",
                "birth on the territory",
                "citizenship by birth",
                "iso identifier",
                "iso numeric",
                "international dial code"
        );
    }

    public boolean referencesImage(String hintText) {
        String normalized = normalize(hintText);
        if (normalized == null) {
            return false;
        }
        return containsAny(normalized,
                "this image",
                "this photo",
                "this picture",
                "seen here",
                "seen in this",
                "shown here",
                "shown above",
                "pictured here",
                "pictured above",
                "pictured is",
                "depicted here",
                "depicted above",
                "look at this",
                "in this view"
        );
    }

    public boolean isFormulaicHint(String hintText) {
        String normalized = normalize(hintText);
        if (normalized == null) {
            return true;
        }
        return containsAny(normalized,
                "this territory is",
                "despite being",
                "the territory is",
                "the average",
                "ranked ",
                "geographic data confirms",
                "political status",
                "cultural profile",
                "standard geographic",
                "well known fact",
                "confirmed by",
                "while this territory",
                "this collection of islands",
                "these scattered specks",
                "while technically located"
        );
    }

    public boolean isNumericHeavyHint(String hintText) {
        if (hintText == null || hintText.isBlank()) {
            return false;
        }
        long digits = hintText.chars().filter(Character::isDigit).count();
        String normalized = normalize(hintText);
        return digits >= 5 || containsAny(normalized,
                "ranked ",
                "per 100,000",
                "global average",
                "average of ",
                "arrivals annually",
                "years above",
                "years below",
                "percent",
                "km²",
                "km2",
                "square kilometers",
                "gdp",
                "population growth rate"
        );
    }

    public boolean isBareStatisticClue(String hintText) {
        String normalized = normalize(hintText);
        if (normalized == null) {
            return true;
        }
        return isNumericHeavyHint(hintText)
                && !containsAny(normalized,
                "small enough to fit",
                "under the wing",
                "rather than",
                "instead of",
                "defined by",
                "you will find no",
                "synonymous with",
                "at a premium",
                "balancing act",
                "thought of as",
                "fit within",
                "compared with");
    }

    public boolean hasRepetitiveOpener(String hintText) {
        String normalized = normalize(hintText);
        if (normalized == null) {
            return false;
        }
        String opener = normalized.substring(0, Math.min(60, normalized.length()));
        return containsAny(opener,
                "this nation is home to",
                "this country is home to",
                "this is the only country",
                "this is the only nation",
                "this nation boasts",
                "this country boasts",
                "home to the world",
                "known for its rich",
                "known for its diverse",
                "known for its vibrant",
                "known for its unique"
        );
    }

    public boolean isGenericThemeHint(String hintText) {
        String normalized = normalize(hintText);
        if (normalized == null) {
            return true;
        }
        return containsAny(normalized,
                "no terrestrial borders",
                "only neighbors are the waves",
                "only neighbor is the open ocean",
                "maritime solitude",
                "scattered far and wide",
                "scattered specks of land",
                "managed by a power located thousands of miles away",
                "global finance",
                "aging society",
                "tiny but densely settled",
                "small but densely settled",
                "far flung landmasses",
                "distant federal capital that governs them"
        );
    }

    public boolean isInterestingClue(String hintText) {
        if (hintText == null || hintText.isBlank()) {
            return false;
        }
        String normalized = normalize(hintText);
        if (normalized == null) {
            return false;
        }
        if (isFormulaicHint(hintText) || isBareStatisticClue(hintText)) {
            return false;
        }
        if (containsAny(normalized,
                "small enough to fit",
                "rather than",
                "instead of",
                "defined by",
                "under the wing",
                "at a premium",
                "balancing act",
                "mirroring",
                "synonymous with",
                "fit within",
                "not to each other",
                "holding on to less than",
                "you will find no")) {
            return true;
        }
        return hasSpecificitySignals(hintText);
    }

    private boolean hasSpecificitySignals(String hintText) {
        String[] words = hintText.split("\\s+");
        int properNounCount = 0;
        for (int i = 0; i < words.length; i++) {
            String word = words[i].replaceAll("[^\\p{L}]", "");
            if (word.length() < 3) {
                continue;
            }
            boolean startsWithUpper = Character.isUpperCase(word.charAt(0));
            boolean atSentenceStart = i == 0 || words[i - 1].endsWith(".") || words[i - 1].endsWith("\"");
            if (startsWithUpper && !atSentenceStart) {
                properNounCount++;
            }
        }
        boolean hasQuotedText = hintText.contains("'") || hintText.contains("\"");
        return properNounCount >= 2 || (properNounCount == 1 && hasQuotedText);
    }

    public int countryDifficultyFloor(Country country) {
        return difficultyFloorForModifier(country != null ? country.getDifficultyModifier() : null);
    }

    public int difficultyFloorForModifier(BigDecimal difficultyModifier) {
        double modifier = difficultyModifier != null ? difficultyModifier.doubleValue() : 1.0;

        if (modifier >= 1.45) {
            return 60;
        }
        if (modifier >= 1.30) {
            return 54;
        }
        if (modifier >= 1.15) {
            return 46;
        }
        if (modifier >= 1.00) {
            return 38;
        }
        if (modifier >= 0.80) {
            return 30;
        }
        if (modifier >= 0.60) {
            return 22;
        }
        if (modifier >= 0.45) {
            return 16;
        }
        return 12;
    }

    public int accessibleDifficultyUpperBound(int countryFloor) {
        return Math.clamp(countryFloor + 10, 28, 100);
    }

    public int hardDifficultyLowerBound(int countryFloor) {
        return Math.clamp(countryFloor + 18, 65, 100);
    }

    public int targetDistinctCategoryCount(int requestedProposalCount) {
        if (requestedProposalCount <= 3) {
            return requestedProposalCount;
        }
        if (requestedProposalCount <= 6) {
            return 3;
        }
        if (requestedProposalCount <= 9) {
            return 4;
        }
        return 5;
    }

    public int targetImageClueCount(int requestedProposalCount, double targetImageShare) {
        if (requestedProposalCount <= 0 || targetImageShare <= 0.0) {
            return 0;
        }
        return Math.max(1, (int) Math.round(requestedProposalCount * targetImageShare));
    }

    public int targetAccessibleClueCount(int requestedProposalCount, int countryFloor) {
        if (requestedProposalCount <= 3) {
            return 0;
        }
        return countryFloor >= 54 ? 1 : 2;
    }

    public int targetHardClueCount(int requestedProposalCount, int countryFloor) {
        if (requestedProposalCount <= 3) {
            return countryFloor >= 46 ? 1 : 0;
        }
        return countryFloor >= 54 ? 2 : 1;
    }

    public int expertDifficultyLowerBound(int countryFloor) {
        return Math.clamp(countryFloor + EXPERT_DIFFICULTY_OFFSET, EXPERT_DIFFICULTY_FLOOR, 100);
    }

    public int targetExpertClueCount(int requestedProposalCount, int countryFloor) {
        if (requestedProposalCount < EXPERT_MIN_BATCH_SIZE) {
            return 0;
        }
        return countryFloor >= EXPERT_OBSCURE_COUNTRY_FLOOR ? 0 : 1;
    }

    public int themeDifficultyDelta(String category, String hintType, String hintText) {
        int delta = switch (category != null ? category : "GENERAL") {
            case "HISTORY" -> 18;
            case "POLITICS", "NATURE", "LANDMARKS", "FOOD", "SPORTS" -> 16;
            case "CULTURE" -> 12;
            case "INFRASTRUCTURE", "ENVIRONMENT", "HEALTH" -> 10;
            case "GEOGRAPHY", "DEMOGRAPHICS" -> 8;
            case "ECONOMY" -> 6;
            case "TRIVIA" -> -6;
            default -> 0;
        };

        delta += switch (hintType != null ? hintType : "ABSOLUTE_FACT") {
            case "UNIQUE_FEATURE" -> 14;
            case "HISTORICAL" -> 16;
            case "CULTURAL" -> 10;
            case "SUPERLATIVE" -> 12;
            case "COMPARISON", "PERCENTILE", "RANKING", "RELATIVE_FACT" -> 8;
            case "IMAGE_FACT", "PANORAMA" -> 6;
            default -> 0;
        };

        String family = factFamilyKey(category, hintType, hintText);
        if (family.startsWith("admin:")) {
            delta -= 18;
        }
        if (family.equals("geography:ocean_isolation")
                || family.equals("geography:dispersed_archipelago")
                || family.equals("politics:legal_status")) {
            delta += 10;
        }
        if (family.equals("history:strategic_outpost")
                || family.equals("history:name_origin")
                || family.equals("culture:flag_symbol")
                || family.equals("nature:national_symbol")) {
            delta += 12;
        }
        if (family.equals("economy:service_economy")
                || family.equals("demographics:age_structure")
                || family.equals("environment:density")) {
            delta += 6;
        }

        if (isGenericThemeHint(hintText)) {
            delta -= 8;
        }
        if (isLowSignalHint(hintText)) {
            delta -= 10;
        }
        if (isNumericHeavyHint(hintText)) {
            delta += 6;
        }
        if (isInterestingClue(hintText)) {
            delta += 6;
        }

        return clamp(delta);
    }

    public String factFamilyKey(String category, String hintType, String hintText) {
        String normalized = normalize(hintText);
        if (normalized == null) {
            return "empty";
        }
        if (containsAny(normalized, "capital city", "seat of government", "firmly planted in washington")) {
            return "admin:capital";
        }
        if (containsAny(normalized, "currency", "dollar", "euro", "aud", "usd")) {
            return "admin:currency";
        }
        if (containsAny(normalized, "phone prefix", "dialing", "calling code", "international dial code")) {
            return "admin:phone_prefix";
        }
        if (containsAny(normalized, "driving on the", "driving side", "right side of the road", "left side of the road")) {
            return "admin:driving_side";
        }
        if (containsAny(normalized, "time zone", "timezone", "distinct timezones")) {
            return "admin:timezone";
        }
        if (containsAny(normalized, "language code", "english speaking administrative code", "specific code")) {
            return "admin:language_code";
        }
        if (containsAny(normalized, "no terrestrial borders", "only neighbors are the waves", "open ocean", "embraced by water")) {
            return "geography:ocean_isolation";
        }
        if (containsAny(normalized, "scattered", "widely dispersed", "far flung", "thousands of miles apart", "span from")) {
            return "geography:dispersed_archipelago";
        }
        if (containsAny(normalized, "wildlife refuges", "no permanent residents", "uninhabited", "lack of permanent residents")) {
            return "politics:no_permanent_residents";
        }
        if (containsAny(normalized, "unincorporated", "unorganized", "crown dependency", "administrative banner", "governing nation")) {
            return "politics:legal_status";
        }
        if (containsAny(normalized, "strategic waypoint", "naval conflict", "pacific theater", "commercial airline stopover", "wartime")) {
            return "history:strategic_outpost";
        }
        if (containsAny(normalized, "named by", "name traces back", "james cook", "duke of norfolk")) {
            return "history:name_origin";
        }
        if (containsAny(normalized, "flag", "pine tree dominates", "center of this territory s flag")) {
            return "culture:flag_symbol";
        }
        if (containsAny(normalized, "national symbol", "endemic conifer", "norfolk island pine")) {
            return "nature:national_symbol";
        }
        if (containsAny(normalized, "population", "residents", "sports stadium")) {
            return "demographics:population_size";
        }
        if (containsAny(normalized, "under 15", "over 65", "aging society", "aging demographic")) {
            return "demographics:age_structure";
        }
        if (containsAny(normalized, "fertility rate")) {
            return "demographics:fertility";
        }
        if (containsAny(normalized, "population growth rate", "population growth")) {
            return "demographics:population_growth";
        }
        if (containsAny(normalized, "service sector", "global finance", "offshore finance", "specialized service based")) {
            return "economy:service_economy";
        }
        if (containsAny(normalized, "agriculture", "farming")) {
            return "economy:agriculture_share";
        }
        if (containsAny(normalized, "industrial activity", "industry")) {
            return "economy:industry_share";
        }
        if (containsAny(normalized, "unemployment", "labor market")) {
            return "economy:unemployment";
        }
        if (containsAny(normalized, "gdp", "economic output", "economy has shown resilience", "expansion in gdp")) {
            return "economy:gdp";
        }
        if (containsAny(normalized, "life expectancy", "live to an average")) {
            return "health:life_expectancy";
        }
        if (containsAny(normalized, "crowded patches", "population density", "densely settled")) {
            return "environment:density";
        }
        if (containsAny(normalized, "agricultural purposes", "land area is still utilized")) {
            return "geography:land_use";
        }

        List<String> keywords = extractKeywords(hintText);
        String prefix = stageSafeCategory(category);
        if (keywords.isEmpty()) {
            return prefix + ":" + stageSafeHintType(hintType);
        }
        return prefix + ":" + String.join("_", keywords.stream().limit(3).toList());
    }

    public List<String> extractKeywords(String hintText) {
        String normalized = normalize(hintText);
        if (normalized == null) {
            return List.of();
        }
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        for (String token : normalized.split("\\s+")) {
            if (token.length() < 4 || STOP_WORDS.contains(token)) {
                continue;
            }
            keywords.add(token);
            if (keywords.size() >= 8) {
                break;
            }
        }
        return List.copyOf(keywords);
    }

    public double hintSimilarityScore(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        if (normalizedLeft == null || normalizedRight == null) {
            return 0.0;
        }
        if (normalizedLeft.equals(normalizedRight)) {
            return 1.0;
        }

        String leftFamily = factFamilyKey(null, null, left);
        String rightFamily = factFamilyKey(null, null, right);
        double familyBonus = leftFamily.equals(rightFamily) ? 0.45 : 0.0;

        Set<String> leftWords = new LinkedHashSet<>(extractKeywords(left));
        Set<String> rightWords = new LinkedHashSet<>(extractKeywords(right));
        if (leftWords.isEmpty() || rightWords.isEmpty()) {
            return familyBonus
                    + (normalizedLeft.startsWith(normalizedRight) || normalizedRight.startsWith(normalizedLeft) ? 0.40 : 0.0);
        }

        List<String> common = new ArrayList<>(leftWords);
        common.retainAll(rightWords);
        double jaccard = (double) common.size() / (leftWords.size() + rightWords.size() - common.size());
        double prefixBonus = normalizedLeft.substring(0, Math.min(32, normalizedLeft.length()))
                .equals(normalizedRight.substring(0, Math.min(32, normalizedRight.length())))
                ? 0.25
                : 0.0;
        return jaccard + prefixBonus + familyBonus;
    }

    private String stageSafeCategory(String category) {
        return category != null && !category.isBlank() ? category.toLowerCase(Locale.ROOT) : "general";
    }

    private String stageSafeHintType(String hintType) {
        return hintType != null && !hintType.isBlank() ? hintType.toLowerCase(Locale.ROOT) : "fact";
    }

    private boolean containsAny(String text, String... patterns) {
        if (text == null) {
            return false;
        }
        for (String pattern : patterns) {
            if (text.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private int clamp(int value) {
        return Math.clamp(value, -24, 34);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s+]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.isBlank() ? null : normalized;
    }
}
