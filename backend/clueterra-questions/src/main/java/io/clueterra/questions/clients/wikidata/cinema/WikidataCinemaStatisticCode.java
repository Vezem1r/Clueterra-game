package io.clueterra.questions.clients.wikidata.cinema;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WikidataCinemaStatisticCode {

    public static final String PREFIX = "WIKIDATA_CINEMA_";

    private static final Pattern PERSON_PATTERN = Pattern.compile(
            "^WIKIDATA_CINEMA_([AD])_(Q[1-9][0-9]*)$");
    private static final Pattern WORK_PATTERN = Pattern.compile(
            "^WIKIDATA_CINEMA_([AD])W_(Q[1-9][0-9]*)_(Q[1-9][0-9]*)$");

    private WikidataCinemaStatisticCode() {
    }

    public static String person(WikidataCinemaFactType factType, String personItemId) {
        return PREFIX + factType.statisticRoleCode() + "_" + personItemId;
    }

    public static String work(WikidataCinemaFactType factType,
                              String personItemId,
                              String workItemId) {
        return PREFIX + factType.statisticRoleCode() + "W_" + personItemId + "_" + workItemId;
    }

    public static Optional<WikidataCinemaPersonCode> parsePerson(String indicatorCode) {
        Matcher matcher = PERSON_PATTERN.matcher(Optional.ofNullable(indicatorCode).orElse(""));
        if (!matcher.matches()) {
            return Optional.empty();
        }
        WikidataCinemaFactType factType = WikidataCinemaFactType.fromStatisticRoleCode(matcher.group(1));
        return Optional.ofNullable(factType)
                .map(type -> new WikidataCinemaPersonCode(type, matcher.group(2)));
    }

    public static Optional<WikidataCinemaWorkCode> parseWork(String indicatorCode) {
        Matcher matcher = WORK_PATTERN.matcher(Optional.ofNullable(indicatorCode).orElse(""));
        if (!matcher.matches()) {
            return Optional.empty();
        }
        WikidataCinemaFactType factType = WikidataCinemaFactType.fromStatisticRoleCode(matcher.group(1));
        return Optional.ofNullable(factType)
                .map(type -> new WikidataCinemaWorkCode(type, matcher.group(2), matcher.group(3)));
    }
}
