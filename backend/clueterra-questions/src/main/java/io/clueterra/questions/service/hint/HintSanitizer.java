package io.clueterra.questions.service.hint;

import io.clueterra.questions.entity.Country;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class HintSanitizer {

    private static final String REPLACEMENT = "this country";
    private static final Set<String> RISKY_ALPHA3_CODES = Set.of("AND", "ARE", "CAN");

    public String sanitize(String hintText, Country country) {
        if (hintText == null || country == null) {
            return null;
        }

        String result = hintText;
        int replacementCount = 0;

        HintTextReplacement englishNameResult = replaceCountryPhrase(result, country.getNameEn());
        result = englishNameResult.text();
        replacementCount += englishNameResult.replacements();

        if (country.getNameNative() != null
                && !country.getNameNative().equalsIgnoreCase(country.getNameEn())) {
            HintTextReplacement nativeNameResult = replaceCountryPhrase(result, country.getNameNative());
            result = nativeNameResult.text();
            replacementCount += nativeNameResult.replacements();
        }

        String alpha2 = normalizeIsoCode(country.getIsoAlpha2());
        if (alpha2 != null) {
            HintTextReplacement alpha2Result = replaceIsoCode(result, alpha2);
            result = alpha2Result.text();
            replacementCount += alpha2Result.replacements();
        }

        String alpha3 = normalizeIsoCode(country.getIsoAlpha3());
        if (shouldReplaceAlpha3(alpha3)) {
            HintTextReplacement alpha3Result = replaceIsoCode(result, alpha3);
            result = alpha3Result.text();
            replacementCount += alpha3Result.replacements();
        }

        if (replacementCount > 2) {
            return null;
        }

        if (containsCountryLeak(result, country)) {
            return null;
        }

        result = cleanup(result);
        return result == null || result.isBlank() ? null : result;
    }

    private boolean containsCountryLeak(String text, Country country) {
        if (CountryNameMatcher.revealsCountry(text, country)) {
            return true;
        }

        String alpha2 = normalizeIsoCode(country.getIsoAlpha2());
        if (alpha2 != null && containsIsoToken(text, alpha2)) {
            return true;
        }

        String alpha3 = normalizeIsoCode(country.getIsoAlpha3());
        return shouldReplaceAlpha3(alpha3) && containsIsoToken(text, alpha3);
    }

    private HintTextReplacement replaceCountryPhrase(String text, String target) {
        if (target == null || target.isBlank()) {
            return new HintTextReplacement(text, 0);
        }

        HintTextReplacement withArticle = replaceAll(text, compilePhrasePattern("the " + target));
        HintTextReplacement plain = replaceAll(withArticle.text(), compilePhrasePattern(target));
        return new HintTextReplacement(plain.text(), withArticle.replacements() + plain.replacements());
    }

    private HintTextReplacement replaceIsoCode(String text, String code) {
        if (code == null || code.isBlank()) {
            return new HintTextReplacement(text, 0);
        }

        HintTextReplacement withArticle = replaceAll(text, compileIsoWithArticlePattern(code));
        HintTextReplacement plain = replaceAll(withArticle.text(), compileIsoPattern(code));
        return new HintTextReplacement(plain.text(), withArticle.replacements() + plain.replacements());
    }

    private HintTextReplacement replaceAll(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder buffer = new StringBuilder();
        int replacements = 0;

        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(HintSanitizer.REPLACEMENT));
            replacements++;
        }
        matcher.appendTail(buffer);
        return new HintTextReplacement(buffer.toString(), replacements);
    }

    private boolean containsIsoToken(String text, String code) {
        return compileIsoPattern(code).matcher(text).find()
                || compileIsoPattern("the " + code).matcher(text).find();
    }

    private Pattern compilePhrasePattern(String phrase) {
        String body = tokenizePhrase(phrase);
        int flags = Pattern.UNICODE_CASE;
        flags |= Pattern.CASE_INSENSITIVE;
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + body + "(?![\\p{L}\\p{N}])", flags);
    }

    private Pattern compileIsoPattern(String code) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + tokenizePhrase(code) + "(?![\\p{L}\\p{N}])");
    }

    private Pattern compileIsoWithArticlePattern(String code) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])[Tt]he\\s+" + tokenizePhrase(code) + "(?![\\p{L}\\p{N}])");
    }

    private String tokenizePhrase(String phrase) {
        return Pattern.compile("\\s+")
                .splitAsStream(phrase.trim())
                .filter(token -> !token.isBlank())
                .map(Pattern::quote)
                .collect(Collectors.joining("\\s+"));
    }

    private String normalizeIsoCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private boolean shouldReplaceAlpha3(String alpha3) {
        return alpha3 != null && alpha3.length() == 3 && !RISKY_ALPHA3_CODES.contains(alpha3);
    }

    private String cleanup(String text) {
        if (text == null) {
            return null;
        }

        String result = text;
        result = result.replaceAll("\\s+([,;:!?]|\\.(?![\\p{L}\\p{N}]))", "$1");
        result = result.replaceAll("\\(\\s+", "(");
        result = result.replaceAll("\\s+\\)", ")");
        result = result.replaceAll("\\s{2,}", " ").trim();

        if (!result.isEmpty() && Character.isLowerCase(result.charAt(0))) {
            result = Character.toUpperCase(result.charAt(0)) + result.substring(1);
        }

        return result;
    }
}
