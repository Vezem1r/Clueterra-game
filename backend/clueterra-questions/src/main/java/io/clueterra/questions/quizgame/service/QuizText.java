package io.clueterra.questions.quizgame.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class QuizText {

    private QuizText() {
    }

    public static Map<String, String> capitalized(Map<String, String> names) {
        return names.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                name -> capitalized(name.getValue(), Locale.forLanguageTag(name.getKey())),
                (first, second) -> first,
                LinkedHashMap::new));
    }

    private static String capitalized(String text, Locale locale) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        int first = text.offsetByCodePoints(0, 1);
        return text.substring(0, first).toUpperCase(locale) + text.substring(first);
    }
}
