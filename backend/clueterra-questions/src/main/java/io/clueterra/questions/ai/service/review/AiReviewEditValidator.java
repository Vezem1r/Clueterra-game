package io.clueterra.questions.ai.service.review;

import io.clueterra.questions.ai.model.review.AiReviewCandidate;
import io.clueterra.questions.ai.model.review.AiReviewEditValidation;
import io.clueterra.questions.ai.service.embedding.AiHintEmbeddingService;
import io.clueterra.questions.ai.service.embedding.CosineSimilarity;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class AiReviewEditValidator {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(?<![\\p{L}\\p{N}])\\d+(?:[.,]\\d+)?");
    private static final Pattern WORD_PATTERN = Pattern.compile("\\p{L}[\\p{L}'\\u2019\\p{Pd}]*");
    private static final int MAX_SENTENCES = 2;

    private final Optional<AiHintEmbeddingService> embeddingService;

    public AiReviewEditValidation validate(AiReviewCandidate candidate, String proposedText) {
        List<String> failures = new ArrayList<>();
        String originalText = candidate.hintText();

        if (StringUtils.isBlank(proposedText)) {
            return new AiReviewEditValidation(false, null, "Proposed text is empty");
        }
        if (sentenceCount(proposedText) > MAX_SENTENCES) {
            failures.add("more than two sentences");
        }
        if (!extractNumbers(originalText).equals(extractNumbers(proposedText))) {
            failures.add("numbers changed");
        }
        if (containsTargetCountry(candidate.countryName(), proposedText)) {
            failures.add("target country name in proposed text");
        }
        if (introducesUnverifiedNames(candidate, originalText, proposedText)) {
            failures.add("new name outside allowed context");
        }

        Double similarity = semanticSimilarity(originalText, proposedText);
        if (similarity == null) {
            failures.add("local embedding validation unavailable");
        } else if (embeddingService.isPresent() && similarity < embeddingService.get().relatedThreshold()) {
            failures.add("semantic anchor changed");
        }

        if (failures.isEmpty()) {
            return new AiReviewEditValidation(true, similarity, "Safe text edit validated");
        }
        return new AiReviewEditValidation(false, similarity, String.join("; ", failures));
    }

    private Double semanticSimilarity(String originalText, String proposedText) {
        try {
            if (embeddingService.isEmpty() || !embeddingService.get().isAvailable()) {
                return null;
            }
            List<float[]> vectors = embeddingService.get()
                    .embedAllForGenerationValidation(List.of(originalText, proposedText));
            return vectors.size() == 2 ? CosineSimilarity.between(vectors.get(0), vectors.get(1)) : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public boolean containsTargetCountry(String countryName, String text) {
        if (StringUtils.isBlank(countryName) || StringUtils.isBlank(text)) {
            return false;
        }
        Pattern countryNamePattern = Pattern.compile(
                "(?iu)(?<![\\p{L}\\p{N}])" + Pattern.quote(countryName.strip())
                        + "(?![\\p{L}\\p{N}])");
        return countryNamePattern.matcher(text).find();
    }

    private boolean introducesUnverifiedNames(
            AiReviewCandidate candidate,
            String originalText,
            String proposedText) {
        Set<String> allowed = new HashSet<>(capitalizedWords(originalText));
        allowed.addAll(capitalizedWords(candidate.countryRegion()));
        allowed.addAll(capitalizedWords(candidate.countrySubregion()));
        allowed.addAll(capitalizedWords(candidate.countryCapital()));
        return capitalizedWords(proposedText).stream().anyMatch(word -> !allowed.contains(word));
    }

    private Set<String> capitalizedWords(String text) {
        if (StringUtils.isBlank(text)) {
            return Set.of();
        }
        Set<String> words = new HashSet<>();
        Matcher matcher = WORD_PATTERN.matcher(text);
        while (matcher.find()) {
            String word = matcher.group();
            boolean sentenceStart = matcher.start() == 0
                    || text.substring(0, matcher.start()).stripTrailing().matches("(?s).*?[.!?]$");
            if (!sentenceStart && Character.isUpperCase(word.codePointAt(0))) {
                words.add(word.toLowerCase(Locale.ROOT));
            }
        }
        return words;
    }

    private List<String> extractNumbers(String text) {
        if (text == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        while (matcher.find()) {
            values.add(matcher.group().replace(',', '.'));
        }
        values.sort(String::compareTo);
        return values;
    }

    private int sentenceCount(String text) {
        String[] sentences = text.strip().split("(?<=[.!?])\\s+");
        return (int) Arrays.stream(sentences).filter(StringUtils::isNotBlank).count();
    }
}
