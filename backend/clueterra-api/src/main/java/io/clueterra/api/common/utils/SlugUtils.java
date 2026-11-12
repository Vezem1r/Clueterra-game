package io.clueterra.api.common.utils;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SlugUtils {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_SLUG_CHARACTERS = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("^-+|-+$");

    public static String slugify(String value) {
        if (value == null) {
            return "";
        }
        String ascii = COMBINING_MARKS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD))
                .replaceAll("")
                .toLowerCase(Locale.ROOT);
        String hyphenated = NON_SLUG_CHARACTERS.matcher(ascii).replaceAll("-");
        return EDGE_HYPHENS.matcher(hyphenated).replaceAll("");
    }
}
