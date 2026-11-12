package io.clueterra.questions.clients.wikidata.cinema;

import io.clueterra.questions.clients.common.dto.RawStatistic;
import io.clueterra.questions.clients.wikidata.WikidataStatementService;
import io.clueterra.questions.entity.enums.Category;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WikidataCinemaService {

    private static final BigDecimal CONFIDENCE = new BigDecimal("0.98");
    private static final BigDecimal WEIGHT = new BigDecimal("0.94");
    private static final int ENRICHED_PEOPLE_PER_COUNTRY = 20;
    private static final int FACTS_PER_COUNTRY = 12;

    private final WikidataCinemaQueryService queryService;
    private final WikidataStatementService statementService;

    public Map<String, List<WikidataCinemaFact>> fetchAll(
            String queryUrl,
            String actionApiUrl,
            Map<String, String> countryItemIdByCode,
            Duration timeout) {
        Map<String, String> countryCodeByItemId = countryItemIdByCode.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey, (left, right) -> left));
        List<WikidataCinemaPersonCandidate> discovered = queryService.fetchAllPeople(queryUrl, timeout).stream()
                .filter(candidate -> countryCodeByItemId.containsKey(candidate.countryItemId()))
                .toList();
        List<WikidataCinemaPersonCandidate> selected = takePerCountry(
                discovered, ENRICHED_PEOPLE_PER_COUNTRY);

        Map<String, List<WikidataCinemaFact>> facts = buildFacts(
                selected, countryCodeByItemId, queryUrl, actionApiUrl, timeout);
        log.info("[WikidataCinema] Discovered {} birthplace-matched candidates, retained {} and resolved {} facts for {} countries",
                discovered.size(),
                selected.size(),
                facts.values().stream().mapToInt(List::size).sum(),
                facts.size());
        return facts;
    }

    public List<WikidataCinemaFact> fetchCountry(
            String countryCode,
            String countryItemId,
            String queryUrl,
            String actionApiUrl,
            Duration timeout) {
        List<WikidataCinemaPersonCandidate> selected = queryService.fetchPeopleForCountry(
                        queryUrl, countryItemId, timeout).stream()
                .limit(ENRICHED_PEOPLE_PER_COUNTRY)
                .toList();
        return buildFacts(
                selected,
                Map.of(countryItemId, countryCode),
                queryUrl,
                actionApiUrl,
                timeout).getOrDefault(countryCode, List.of());
    }

    public List<RawStatistic> toStatistics(List<WikidataCinemaFact> facts) {
        if (facts == null || facts.isEmpty()) {
            return List.of();
        }
        return facts.stream()
                .flatMap(fact -> List.of(
                        personStatistic(fact),
                        workStatistic(
                                fact,
                                fact.workOneItemId(),
                                fact.workOne(),
                                fact.workOneSitelinks()),
                        workStatistic(
                                fact,
                                fact.workTwoItemId(),
                                fact.workTwo(),
                                fact.workTwoSitelinks()))
                        .stream())
                .toList();
    }

    private Map<String, List<WikidataCinemaFact>> buildFacts(
            List<WikidataCinemaPersonCandidate> candidates,
            Map<String, String> countryCodeByItemId,
            String queryUrl,
            String actionApiUrl,
            Duration timeout) {
        if (candidates.isEmpty()) {
            return Map.of();
        }

        Map<String, List<WikidataCinemaWorkCandidate>> worksByTarget = queryService.fetchWorks(
                        queryUrl, candidates, timeout).stream()
                .collect(Collectors.groupingBy(work -> targetKey(work.personItemId(), work.factType())));
        List<WikidataCinemaSelection> selections = candidates.stream()
                .map(candidate -> bestSelection(
                        candidate,
                        countryCodeByItemId.get(candidate.countryItemId()),
                        worksByTarget))
                .flatMap(Optional::stream)
                .collect(Collectors.groupingBy(
                        WikidataCinemaSelection::countryCode,
                        LinkedHashMap::new,
                        Collectors.toList()))
                .values().stream()
                .flatMap(countrySelections -> countrySelections.stream()
                        .sorted(selectionComparator())
                        .limit(FACTS_PER_COUNTRY))
                .toList();
        if (selections.isEmpty()) {
            return Map.of();
        }

        Set<String> itemIds = selections.stream()
                .flatMap(selection -> selection.referencedItemIds().stream())
                .collect(Collectors.toUnmodifiableSet());
        Map<String, String> labels = statementService.resolveEnglishLabels(actionApiUrl, itemIds, timeout);
        Map<String, List<WikidataCinemaFact>> byCountry = selections.stream()
                .map(selection -> resolve(selection, labels))
                .flatMap(Optional::stream)
                .collect(Collectors.groupingBy(
                        WikidataCinemaFact::countryCode,
                        LinkedHashMap::new,
                        Collectors.toList()));
        return immutableLists(byCountry);
    }

    private Optional<WikidataCinemaSelection> bestSelection(
            WikidataCinemaPersonCandidate candidate,
            String countryCode,
            Map<String, List<WikidataCinemaWorkCandidate>> worksByTarget) {
        if (!StringUtils.hasText(countryCode)) {
            return Optional.empty();
        }
        return candidate.factTypes().stream()
                .map(factType -> selectionFor(
                        candidate,
                        countryCode,
                        factType,
                        worksByTarget.getOrDefault(targetKey(candidate.personItemId(), factType), List.of())))
                .flatMap(Optional::stream)
                .max(Comparator.comparingInt(WikidataCinemaSelection::workRecognition)
                        .thenComparing(selection -> selection.factType() == WikidataCinemaFactType.DIRECTOR_WORKS));
    }

    private Optional<WikidataCinemaSelection> selectionFor(
            WikidataCinemaPersonCandidate candidate,
            String countryCode,
            WikidataCinemaFactType factType,
            List<WikidataCinemaWorkCandidate> works) {
        List<WikidataCinemaWorkCandidate> topWorks = new ArrayList<>(works.stream()
                .collect(Collectors.toMap(
                        WikidataCinemaWorkCandidate::workItemId,
                        Function.identity(),
                        (first, second) -> first.sitelinks() >= second.sitelinks() ? first : second,
                        LinkedHashMap::new))
                .values()).stream()
                .sorted(Comparator.comparingInt(WikidataCinemaWorkCandidate::sitelinks).reversed()
                        .thenComparing(WikidataCinemaWorkCandidate::workItemId))
                .limit(2)
                .toList();
        if (topWorks.size() < 2) {
            return Optional.empty();
        }
        return Optional.of(new WikidataCinemaSelection(
                countryCode,
                candidate.personItemId(),
                factType,
                topWorks.get(0).workItemId(),
                topWorks.get(1).workItemId(),
                candidate.sitelinks(),
                topWorks.get(0).sitelinks(),
                topWorks.get(1).sitelinks()));
    }

    private Optional<WikidataCinemaFact> resolve(
            WikidataCinemaSelection selection,
            Map<String, String> labels) {
        String subject = labels.get(selection.personItemId());
        String workOne = labels.get(selection.workOneItemId());
        String workTwo = labels.get(selection.workTwoItemId());
        if (!StringUtils.hasText(subject)
                || !StringUtils.hasText(workOne)
                || !StringUtils.hasText(workTwo)) {
            log.warn("[WikidataCinema] Skipping {} because one or more English labels are missing",
                    selection.personItemId());
            return Optional.empty();
        }
        int recognition = recognitionScore(selection);
        double confidence = Math.min(0.99, 0.94 + recognition / 2000.0);
        return Optional.of(new WikidataCinemaFact(
                selection.factType().name() + "_" + selection.personItemId(),
                selection.countryCode(),
                selection.factType(),
                selection.personItemId(),
                subject,
                selection.workOneItemId(),
                workOne,
                selection.workOneSitelinks(),
                selection.workTwoItemId(),
                workTwo,
                selection.workTwoSitelinks(),
                selection.personSitelinks(),
                recognition,
                confidence));
    }

    private RawStatistic personStatistic(WikidataCinemaFact fact) {
        return new RawStatistic(
                WikidataCinemaStatisticCode.person(fact.factType(), fact.ambiguityKey()),
                fact.factType().personIndicatorName(),
                Category.CULTURE,
                BigDecimal.valueOf(fact.recognitionScore()),
                fact.subject(),
                "recognition score",
                null,
                BigDecimal.valueOf(fact.confidence()),
                WEIGHT);
    }

    private RawStatistic workStatistic(WikidataCinemaFact fact,
                                       String workItemId,
                                       String title,
                                       int sitelinks) {
        return new RawStatistic(
                WikidataCinemaStatisticCode.work(fact.factType(), fact.ambiguityKey(), workItemId),
                fact.factType().workIndicatorName(fact.subject()),
                Category.CULTURE,
                BigDecimal.valueOf(sitelinks),
                title,
                "Wikidata sitelinks",
                null,
                CONFIDENCE.min(BigDecimal.valueOf(fact.confidence())),
                WEIGHT);
    }

    private List<WikidataCinemaPersonCandidate> takePerCountry(
            List<WikidataCinemaPersonCandidate> candidates,
            int limit) {
        return candidates.stream()
                .collect(Collectors.groupingBy(
                        WikidataCinemaPersonCandidate::countryItemId,
                        LinkedHashMap::new,
                        Collectors.toList()))
                .values().stream()
                .flatMap(countryCandidates -> countryCandidates.stream()
                        .sorted(Comparator.comparingInt(WikidataCinemaPersonCandidate::sitelinks).reversed()
                                .thenComparing(WikidataCinemaPersonCandidate::personItemId))
                        .limit(limit))
                .toList();
    }

    private Comparator<WikidataCinemaSelection> selectionComparator() {
        return Comparator.comparingInt(this::recognitionScore).reversed()
                .thenComparing(WikidataCinemaSelection::personItemId);
    }

    private int recognitionScore(WikidataCinemaSelection selection) {
        double person = normalizedSitelinks(selection.personSitelinks());
        double works = normalizedSitelinks(
                (selection.workOneSitelinks() + selection.workTwoSitelinks()) / 2.0);
        return Math.clamp((int) Math.round(25 + person * 50 + works * 25), 25, 100);
    }

    private double normalizedSitelinks(double sitelinks) {
        return Math.min(1.0, Math.log1p(Math.max(0, sitelinks)) / Math.log(201));
    }

    private Map<String, List<WikidataCinemaFact>> immutableLists(
            Map<String, List<WikidataCinemaFact>> source) {
        Map<String, List<WikidataCinemaFact>> copy = source.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue()),
                        (left, right) -> left,
                        LinkedHashMap::new));
        return Map.copyOf(copy);
    }

    private String targetKey(String personItemId, WikidataCinemaFactType factType) {
        return personItemId + "|" + factType.name();
    }
}
