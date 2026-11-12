package io.clueterra.questions.service.deduplication;

import io.clueterra.questions.repository.HintRepository;
import io.clueterra.questions.service.deduplication.dto.DeduplicationResult;
import io.clueterra.questions.service.deduplication.dto.RejectedHint;
import io.clueterra.questions.service.deduplication.dto.RejectionReason;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeduplicationService {

    private final HintRepository hintRepository;

    private static final double SIMILARITY_THRESHOLD = 0.70;
    private static final double SAME_PATTERN_SIMILARITY_THRESHOLD = 0.70;
    private static final BigDecimal MIN_QUALITY_THRESHOLD = BigDecimal.valueOf(0.40);
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "is", "are", "was", "were", "has", "have", "had",
            "in", "on", "at", "to", "for", "of", "with", "by", "from", "as",
            "this", "that", "it", "its", "and", "or", "but", "not", "no",
            "one", "country", "world", "global", "region", "ranked"
    );
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern IGNORED_PUNCTUATION = Pattern.compile("[.,!?;:'\"\\-()\\[\\]{}]");
    private static final HexFormat HEX = HexFormat.of();

    public DeduplicationResult deduplicate(Long countryId, List<CandidateHint> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return DeduplicationResult.of(List.of(), List.of());
        }

        List<CandidateHint> accepted = new ArrayList<>();
        List<RejectedHint> rejected = new ArrayList<>();
        List<CandidateHint> toRefresh = new ArrayList<>();

        Set<String> existingHashes = hintRepository.findContentHashesByCountryId(countryId);
        List<String> existingTexts = hintRepository.findActiveTextsByCountryId(countryId);
        Set<String> existingRenderedTexts = new HashSet<>();
        Map<String, List<String>> existingTextsBySemanticHash = new HashMap<>();
        for (String existingText : existingTexts) {
            String renderedText = canonicalizeRenderedText(existingText);
            if (renderedText != null) {
                existingRenderedTexts.add(renderedText);
            }
            indexBySemanticHash(existingTextsBySemanticHash, existingText);
        }

        Set<String> batchHashes = new HashSet<>();
        Set<String> batchRenderedTexts = new HashSet<>();
        List<String> batchTexts = new ArrayList<>();
        Map<String, List<String>> batchTextsBySemanticHash = new HashMap<>();
        Map<String, String> indicatorPatterns = new HashMap<>();

        for (CandidateHint candidate : candidates) {
            CheckResult result = checkCandidate(
                    candidate,
                    existingHashes,
                    existingRenderedTexts,
                    existingTexts,
                    existingTextsBySemanticHash,
                    batchHashes,
                    batchRenderedTexts,
                    batchTexts,
                    batchTextsBySemanticHash,
                    indicatorPatterns
            );

            switch (result.outcome()) {
                case ACCEPTED -> {
                    accepted.add(candidate);
                    String hash = generateContentHash(candidate.text());
                    if (hash != null) {
                        batchHashes.add(hash);
                    }
                    String renderedText = canonicalizeRenderedText(candidate.text());
                    if (renderedText != null) {
                        batchRenderedTexts.add(renderedText);
                    }
                    batchTexts.add(candidate.text());
                    indexBySemanticHash(batchTextsBySemanticHash, candidate.text());
                    updateIndicatorPattern(candidate, indicatorPatterns);
                }
                case METADATA_REFRESH -> toRefresh.add(candidate);
                case REJECTED -> rejected.add(result.rejection());
            }
        }

        log.debug("Deduplication for country {}: {} accepted, {} to refresh, {} rejected out of {} candidates",
                countryId, accepted.size(), toRefresh.size(), rejected.size(), candidates.size());

        return DeduplicationResult.of(accepted, rejected, toRefresh);
    }

    private CheckResult checkCandidate(
            CandidateHint candidate,
            Set<String> existingHashes,
            Set<String> existingRenderedTexts,
            List<String> existingTexts,
            Map<String, List<String>> existingTextsBySemanticHash,
            Set<String> batchHashes,
            Set<String> batchRenderedTexts,
            List<String> batchTexts,
            Map<String, List<String>> batchTextsBySemanticHash,
            Map<String, String> indicatorPatterns
    ) {
        if (candidate.text() == null || candidate.text().isBlank()) {
            return CheckResult.rejected(
                    RejectedHint.of(candidate, RejectionReason.EMPTY_TEXT, "Empty or null text"));
        }

        if (candidate.estimatedQuality().compareTo(MIN_QUALITY_THRESHOLD) < 0) {
            return CheckResult.rejected(
                    RejectedHint.of(candidate, RejectionReason.LOW_QUALITY,
                            String.format("Quality %.2f below threshold %.2f",
                                    candidate.estimatedQuality(), MIN_QUALITY_THRESHOLD)));
        }

        String hash = generateContentHash(candidate.text());
        String renderedText = canonicalizeRenderedText(candidate.text());
        String semanticHash = generateSemanticHash(candidate.text());
        if (hash != null) {
            if (batchHashes.contains(hash)) {
                return CheckResult.rejected(
                        RejectedHint.of(candidate, RejectionReason.EXACT_DUPLICATE, "Duplicate within batch"));
            }
            if (existingHashes.contains(hash)) {
                return CheckResult.refresh();
            }
        }
        if (renderedText != null) {
            if (batchRenderedTexts.contains(renderedText)) {
                return CheckResult.rejected(
                        RejectedHint.of(candidate, RejectionReason.EXACT_DUPLICATE, "Duplicate within batch"));
            }
            if (existingRenderedTexts.contains(renderedText)) {
                return CheckResult.refresh();
            }
        }

        String patternKey = createPatternKey(candidate);
        if (patternKey != null && indicatorPatterns.containsKey(patternKey)) {
            String existing = indicatorPatterns.get(patternKey);
            double similarity = calculateSimilarity(candidate.text(), existing);
            if (similarity > SAME_PATTERN_SIMILARITY_THRESHOLD) {
                return CheckResult.rejected(
                        RejectedHint.withSimilarity(candidate, RejectionReason.SAME_INDICATOR_PATTERN,
                                    "Same indicator pattern blocked", existing, similarity));
            }
        }

        for (String existingText : prioritizeBySemanticHash(existingTexts, existingTextsBySemanticHash, semanticHash)) {
            if (isQuickSimilar(candidate.text(), existingText)) {
                double similarity = calculateSimilarity(candidate.text(), existingText);
                if (similarity >= SIMILARITY_THRESHOLD) {
                    return CheckResult.rejected(
                            RejectedHint.withSimilarity(candidate, RejectionReason.HIGH_SIMILARITY,
                                    "Similar to existing hint", existingText, similarity));
                }
            }
        }

        for (String batchText : prioritizeBySemanticHash(batchTexts, batchTextsBySemanticHash, semanticHash)) {
            if (isQuickSimilar(candidate.text(), batchText)) {
                double similarity = calculateSimilarity(candidate.text(), batchText);
                if (similarity >= SIMILARITY_THRESHOLD) {
                    return CheckResult.rejected(
                            RejectedHint.withSimilarity(candidate, RejectionReason.HIGH_SIMILARITY,
                                    "Similar to batch candidate", batchText, similarity));
                }
            }
        }

        return CheckResult.accepted();
    }

    private String createPatternKey(CandidateHint candidate) {
        if (candidate.sourceIndicator() == null || candidate.lensName() == null) {
            return null;
        }
        return candidate.sourceIndicator() + ":" + candidate.lensName();
    }

    private void updateIndicatorPattern(CandidateHint candidate, Map<String, String> patterns) {
        String key = createPatternKey(candidate);
        if (key != null) {
            patterns.computeIfAbsent(key, k -> candidate.text());
        }
    }

    private boolean isQuickSimilar(String text1, String text2) {
        if (text1 == null || text2 == null) return false;

        int len1 = text1.length();
        int len2 = text2.length();
        if (len1 == 0 || len2 == 0) return false;

        double lengthRatio = (double) Math.min(len1, len2) / Math.max(len1, len2);
        if (lengthRatio < 0.5) return false;

        String prefix1 = normalizeText(text1.substring(0, Math.min(20, len1)));
        String prefix2 = normalizeText(text2.substring(0, Math.min(20, len2)));
        return prefix1.equals(prefix2) || shareSignificantWords(text1, text2);
    }

    private boolean shareSignificantWords(String text1, String text2) {
        Set<String> words1 = extractSignificantWords(text1);
        Set<String> words2 = extractSignificantWords(text2);

        if (words1.isEmpty() || words2.isEmpty()) return false;

        long common = words1.stream().filter(words2::contains).count();
        return common >= Math.min(3, Math.min(words1.size(), words2.size()));
    }

    private Set<String> extractSignificantWords(String text) {
        return WHITESPACE.splitAsStream(normalizeText(text))
                .filter(word -> word.length() >= 4 && !STOP_WORDS.contains(word))
                .collect(Collectors.toSet());
    }

    public String generateContentHash(String text) {
        String normalized = canonicalizeRenderedText(text);
        return normalized == null ? null : sha256Hex(normalized);
    }

    public String generateSemanticHash(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return sha256Hex(normalizeText(text));
    }

    public String generateRevisionHash(String text, String imageUrl) {
        if ((text == null || text.isBlank()) && (imageUrl == null || imageUrl.isBlank())) {
            return null;
        }

        String normalizedText = canonicalizeRenderedText(text);
        String normalizedImageUrl = normalizeImageUrl(imageUrl);
        return sha256Hex((normalizedText != null ? normalizedText : "")
                + "\n--image--\n"
                + (normalizedImageUrl != null ? normalizedImageUrl : ""));
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public String normalizeText(String text) {
        if (text == null) return "";

        String lowerCased = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        String collapsed = WHITESPACE.matcher(lowerCased).replaceAll(" ");
        return IGNORED_PUNCTUATION.matcher(collapsed).replaceAll("").trim();
    }

    public double calculateSimilarity(String text1, String text2) {
        if (text1 == null || text1.isBlank() || text2 == null || text2.isBlank()) {
            return 0.0;
        }

        String norm1 = normalizeText(text1);
        String norm2 = normalizeText(text2);

        if (norm1.isEmpty() || norm2.isEmpty()) {
            return 0.0;
        }

        if (norm1.equals(norm2)) {
            return 1.0;
        }

        double levenshtein = 1.0 - ((double) levenshteinDistance(norm1, norm2) / Math.max(norm1.length(), norm2.length()));
        double jaccard = jaccardSimilarity(norm1, norm2);

        return 0.6 * levenshtein + 0.4 * jaccard;
    }

    private int levenshteinDistance(String s1, String s2) {
        int[] previous = new int[s2.length() + 1];
        int[] current = new int[s2.length() + 1];
        for (int j = 0; j <= s2.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= s1.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= s2.length(); j++) {
                int substitution = previous[j - 1] + (s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, 1 + Math.min(previous[j], current[j - 1]));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[s2.length()];
    }

    private double jaccardSimilarity(String text1, String text2) {
        Set<String> words1 = WHITESPACE.splitAsStream(text1).collect(Collectors.toSet());
        Set<String> words2 = WHITESPACE.splitAsStream(text2).collect(Collectors.toSet());

        if (words1.isEmpty() || words2.isEmpty()) return 0.0;

        long intersection = words1.stream().filter(words2::contains).count();
        long union = words1.size() + words2.size() - intersection;

        return union > 0 ? (double) intersection / union : 0.0;
    }

    private String canonicalizeRenderedText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return WHITESPACE.matcher(Normalizer.normalize(text, Normalizer.Form.NFC)).replaceAll(" ").trim();
    }

    private String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        return WHITESPACE.matcher(Normalizer.normalize(imageUrl, Normalizer.Form.NFC)).replaceAll(" ").trim();
    }

    private void indexBySemanticHash(Map<String, List<String>> bucketedTexts, String text) {
        String semanticHash = generateSemanticHash(text);
        if (semanticHash == null) {
            return;
        }
        bucketedTexts.computeIfAbsent(semanticHash, ignored -> new ArrayList<>()).add(text);
    }

    public boolean isSemanticallyDuplicate(String hintText, List<String> comparisonPool) {
        if (hintText == null || hintText.isBlank() || comparisonPool == null || comparisonPool.isEmpty()) {
            return false;
        }
        for (String existing : comparisonPool) {
            if (calculateSimilarity(hintText, existing) >= SIMILARITY_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    private List<String> prioritizeBySemanticHash(List<String> texts,
                                                  Map<String, List<String>> bucketedTexts,
                                                  String semanticHash) {
        if (texts.isEmpty() || semanticHash == null) {
            return texts;
        }
        List<String> prioritized = bucketedTexts.get(semanticHash);
        if (prioritized == null || prioritized.isEmpty() || prioritized.size() == texts.size()) {
            return texts;
        }

        Set<String> ordered = new LinkedHashSet<>(prioritized);
        ordered.addAll(texts);
        return List.copyOf(ordered);
    }
}
