package io.clueterra.budget.source.wikidata;

import java.util.Locale;

final class WikipediaWikitextParser {

    private static final String INFOBOX_PREFIX = "{{infobox";

    private WikipediaWikitextParser() {
    }

    static String firstInfobox(String wikitext) {
        if (wikitext == null || wikitext.isBlank()) {
            return "";
        }
        int start = wikitext.toLowerCase(Locale.ROOT).indexOf(INFOBOX_PREFIX);
        if (start < 0) {
            return "";
        }
        int depth = 0;
        for (int index = start; index < wikitext.length() - 1; index++) {
            String pair = wikitext.substring(index, index + 2);
            if ("{{".equals(pair)) {
                depth++;
                index++;
            } else if ("}}".equals(pair)) {
                depth--;
                index++;
                if (depth == 0) {
                    return wikitext.substring(start, index + 1);
                }
            }
        }
        return "";
    }
}
