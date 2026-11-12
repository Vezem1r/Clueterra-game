package io.clueterra.questions.ai.service.review;

import io.clueterra.questions.ai.model.review.AiReviewCandidate;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class AiReviewQualityAnalyzer {

    private static final Pattern TERMINAL_PUNCTUATION = Pattern.compile("[.!?…][\\\"')\\]]*$");
    private static final Pattern UNSUPPORTED_HYPE = Pattern.compile(
            "(?i)\\b(of course you have|billions of people|brands? .* everywhere|anywhere else in the world|"
                    + "changed how the world|entire daily routine|mega-brand|every corner)\\b"
    );
    private static final Pattern VAGUE_WORDING = Pattern.compile(
            "(?i)\\b(influenced by both|growing international .* scene|local-influenced|"
                    + "significantly larger|leading green mobility)\\b"
    );
    private static final Pattern GENERIC_AI_VOICE = Pattern.compile(
            "(?i)\\b(whether you realize it or not|imagine a place|nestled|boasts|"
                    + "rich cultural heritage|vibrant tapestry|stands as a testament|"
                    + "not just .{1,60} but also|of course you have)\\b"
    );
    private static final Pattern TIME_SENSITIVE_TEXT = Pattern.compile(
            "(?i)\\b(current|interim|president|prime minister|head of state|head of government)\\b"
    );
    private static final Set<String> COMMON_FACT_INDICATOR_PREFIXES = Set.of(
            "CIA_VOTING_AGE_",
            "CIA_LANDLOCKED_",
            "GEONAMES_ISLAND_NATION_"
    );

    public List<String> analyze(AiReviewCandidate candidate) {
        String text = StringUtils.trimToEmpty(candidate.hintText());
        List<String> signals = new ArrayList<>();
        if (text.isEmpty()) {
            signals.add("EMPTY_TEXT");
            return signals;
        }
        if (!TERMINAL_PUNCTUATION.matcher(text).find()) {
            signals.add("MISSING_TERMINAL_PUNCTUATION");
        }
        if (UNSUPPORTED_HYPE.matcher(text).find()) {
            signals.add("UNSUPPORTED_HYPERBOLE_OR_ABSOLUTE_CLAIM");
        }
        if (VAGUE_WORDING.matcher(text).find()) {
            signals.add("VAGUE_OR_GENERIC_WORDING");
        }
        if (GENERIC_AI_VOICE.matcher(text).find()) {
            signals.add("GENERIC_AI_VOICE_OR_TEMPLATE");
        }
        if (isBareCommonFact(candidate, text)) {
            signals.add("BARE_COMMON_FACT_WITHOUT_COUNTRY_CONTEXT");
        }
        if (TIME_SENSITIVE_TEXT.matcher(text).find() && candidate.sourceYear() == null) {
            signals.add("TIME_SENSITIVE_WITHOUT_SOURCE_YEAR");
        }
        if (isObscureNamedEntityIndicator(candidate.sourceIndicator())
                && candidate.difficulty() != null
                && candidate.difficulty() < 40) {
            signals.add("POSSIBLY_UNDERRATED_OBSCURE_NAMED_ENTITY");
        }
        return List.copyOf(signals);
    }

    private boolean isBareCommonFact(AiReviewCandidate candidate, String text) {
        String indicator = StringUtils.defaultString(candidate.sourceIndicator()).toUpperCase(Locale.ROOT);
        boolean commonFact = COMMON_FACT_INDICATOR_PREFIXES.stream().anyMatch(indicator::startsWith);
        if (!commonFact || text.trim().split("\\s+").length >= 14) {
            return false;
        }
        return !containsIgnoreCase(text, candidate.countryRegion())
                && !containsIgnoreCase(text, candidate.countrySubregion())
                && !containsIgnoreCase(text, candidate.countryCapital());
    }

    private boolean containsIgnoreCase(String text, String value) {
        return StringUtils.isNotBlank(value)
                && text.toLowerCase(Locale.ROOT).contains(value.trim().toLowerCase(Locale.ROOT));
    }

    private boolean isObscureNamedEntityIndicator(String sourceIndicator) {
        if (sourceIndicator == null) {
            return false;
        }
        String normalized = sourceIndicator.toUpperCase(Locale.ROOT);
        return normalized.contains("OPERATOR")
                || normalized.contains("NETWORK")
                || normalized.contains("OBSCURE")
                || normalized.contains("LOCAL_CHAMPION");
    }
}
