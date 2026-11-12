package io.clueterra.budget.source.wikidata;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import io.clueterra.budget.project.ProjectCostKind;

@Component
public class WikipediaInfoboxCostExtractor {

    private static final Pattern COST_FIELD_START =
            Pattern.compile(
                    "(?i)^\\|\\s*(cost|construction[_ ]cost|project[_ ]cost|development[_ ]cost|programme[_"
                            + " ]cost|program[_ ]cost|budget|original[_ ]budget|initial[_ ]budget|approved[_"
                            + " ]budget|final[_ ]cost|actual[_ ]cost|estimated[_ ]cost|current[_ ]estimate|cost[_"
                            + " ]estimate|original[_ ]cost[_ ]estimate|initial[_ ]cost[_"
                            + " ]estimate|financing|funding|contract[_ ]value)\\s*=\\s*(.*)$");
    private static final Pattern FIELD_START =
            Pattern.compile("(?i)^\\|\\s*[a-z][a-z0-9_ ]*\\s*=.*$");
    private static final Pattern REFERENCE_URL =
            Pattern.compile("(?i)(?:\\|\\s*url\\s*=|\\[)\\s*(https?://[^\\s|}\\]<>]+)");
    private static final Pattern NAMED_REFERENCE =
            Pattern.compile("(?i)<ref\\s+name\\s*=\\s*[\"']?([^\"' />]+)[\"']?\\s*/>");
    private static final Pattern NAMED_REFERENCE_TEMPLATE =
            Pattern.compile("(?i)\\{\\{\\s*r\\s*\\|\\s*([^|}]+)");
    private static final String CURRENCY_TOKEN =
            "HK\\$|CN¥|AU\\$|NZ\\$|SG\\$|CA\\$|C\\$|A\\$|R\\$|US\\s*\\$|USD|EUR|GBP|JPY|CNY|RMB|INR|AUD|CAD|CHF|KRW|AED|SAR|RUB|BRL|HKD|SGD|NOK|SEK|DKK|TRY|ZAR|MXN|NZD|PHP|IDR|MYR|THB|PLN|CZK|HUF|ILS|[€£¥₹₩₽₺₱₪$]|د\\.?إ";
    private static final String SIMPLE_SCALE_TOKEN =
            "trillion|billion|million|bn|mn|[mb]";
    private static final String SCALE_TOKEN =
            "trillion|billions?|million(?:s|en)?|milliards?|milliarden?|millón|millones|mil"
                    + " millones|bilhão|bilhões|milhão|milhões|miliard[oi]|milion[ei]|miljard(?:en)?|miljoen(?:en)?|miliard(?:y|ów)?|milion(?:y|ów)?|milyar|milyon|миллиард(?:а|ов)?|миллион(?:а|ов)?|mld|mln|bn|mn|[mb]";
    private static final Pattern AMOUNT =
            Pattern.compile(
                    "(?i)("
                            + CURRENCY_TOKEN
                            + ")\\s*(?:\\||&nbsp;|\\s)*([0-9][0-9.,]*)\\s*("
                            + SIMPLE_SCALE_TOKEN
                            + ")?");
    private static final Pattern AMOUNT_BEFORE_CURRENCY =
            Pattern.compile(
                    "(?i)([0-9][0-9.,]*)\\s*("
                            + SIMPLE_SCALE_TOKEN
                            + ")?\\s*(USD|EUR|GBP|JPY|CNY|RMB|INR|AUD|CAD|CHF|KRW|AED|SAR|RUB|BRL|HKD|SGD|NOK|SEK|DKK|TRY|ZAR|MXN|NZD|PHP|IDR|MYR|THB|PLN|CZK|HUF|ILS)");
    private static final Pattern CURRENCY_RANGE =
            Pattern.compile(
                    "(?iu)("
                            + CURRENCY_TOKEN
                            + ")\\s*([0-9][0-9.,]*)\\s*("
                            + SCALE_TOKEN
                            + ")?\\s*(?:to|[-–—])\\s*(?:("
                            + CURRENCY_TOKEN
                            + ")\\s*)?([0-9][0-9.,]*)\\s*("
                            + SCALE_TOKEN
                            + ")");
    private static final Pattern SCALED_CURRENCY_TEMPLATE =
            Pattern.compile(
                    "(?i)\\{\\{\\s*(US\\$|USD|EUR|GBP|JPY|CNY|RMB|INR|AUD|CAD|CHF|KRW|AED|SAR|RUB|BRL|HKD|SGD|NOK|SEK|DKK|TRY|ZAR|MXN|NZD)\\s*\\|\\s*([0-9][0-9.,]*)\\s*\\|\\s*("
                            + SIMPLE_SCALE_TOKEN
                            + ")(?:\\|[^}]*)?}}");
    private static final Pattern GENERIC_CURRENCY_TEMPLATE =
            Pattern.compile(
                    "(?i)\\{\\{\\s*Currency\\s*\\|\\s*([0-9][0-9.,]*)\\s*("
                            + SIMPLE_SCALE_TOKEN
                            + ")?(?:\\|\\s*(USD|EUR|EURO|GBP|JPY|CNY|RMB|INR|AUD|CAD|CHF|KRW|AED|SAR|RUB|BRL|HKD|SGD|NOK|SEK|DKK|TRY|ZAR|MXN|NZD))?[^}]*}}");
    private static final Pattern ARTICLE_COST_CONTEXT =
            Pattern.compile(
                    "(?i)\\b(cost|costs|costing|budget|budgeted|estimate|estimated|investment)\\b");
    private static final Pattern ARTICLE_DELTA_COST =
            Pattern.compile(
                    "(?i)\\b(cost overrun|budget overrun|over budget by|additional cost|cost increased?"
                            + " by)\\b");
    private static final Pattern ARTICLE_TOTAL_COST =
            Pattern.compile(
                    "(?i)\\b(total|final|actual|overall|eventual)\\s+(?:project\\s+)?cost\\b|\\b(?:ultimately|eventually)\\s+cost\\b");
    private static final Pattern REFERENCE = Pattern.compile("(?is)<ref\\b[^>]*(?:>.*?</ref>|/>)");
    private static final Pattern CITATION_MARKER =
            Pattern.compile("(?is)<ref\\b[^>]*(?:>.*?</ref>|/>)|\\{\\{\\s*r\\s*\\|[^}]+}}");
    private static final Pattern PARTIAL_PROJECT_SCOPE =
            Pattern.compile(
                    "(?iu)\\b(renovation|refurbishment|repair|upgrade|extension|expansion|reopening|conversion|redevelopment|phase|stage|section|segment|package|contract|terminal|station|runway|moderni[sz]ation|réhabilitation|rénovation|agrandissement|tronçon|contrat|estación|ampliación|renovación|fase|tramo|contrato|estação|ampliação|renovação|trecho|реконструкци[яию]|ремонт|расширение|этап|участок|контракт|терминал|станция|ristrutturazione|ampliamento|tratto|contratto|renovatie|uitbreiding|traject|renowacja|rozbudowa|etap|odcinek|kontrakt|yenileme|genişletme|aşama|bölüm|sözleşme)\\b|\\bper[-"
                            + " ](?:room|seat|kilomet(?:er|re)|km|megawatt|mw)\\b");
    private static final Pattern COMPARISON_CONTEXT =
            Pattern.compile(
                    "(?iu)\\b(compared with|compared to|in comparison with|unlike|whereas|similar"
                            + " project|another project|vergleich mit|anders als|comparé à|contrairement à|en"
                            + " comparación con|a diferencia de|em comparação com|ao contrário de|по сравнению"
                            + " с|в отличие от|rispetto a|in tegenstelling tot|w porównaniu z|aksine)\\b");
    private static final Pattern LOCALIZED_AMOUNT_BEFORE_CURRENCY =
            Pattern.compile(
                    "(?iu)([0-9][0-9.,]*)\\s*("
                            + SCALE_TOKEN
                            + ")?\\s*(US"
                            + " dollars?|U\\.?S\\.? dollars?|euros?|pounds?"
                            + " sterling|dollars?|yen|yuan|renminbi|roubles?|rubles?|евро|доллар(?:ов|а)?|руб(?:лей|ля|ль)?|USD|EUR|GBP|JPY|CNY|RMB|INR|AUD|CAD|CHF|KRW|AED|SAR|RUB|BRL|HKD|SGD|NOK|SEK|DKK|TRY|ZAR|MXN|NZD|PHP|IDR|MYR|THB|PLN|CZK|HUF|ILS)");
    private static final Pattern STRICT_WHOLE_PROJECT_COST_CONTEXT =
            Pattern.compile(
                    "(?iu)\\b(construction|project|development|programme|program|mission|total|final|actual|overall|estimated|capital)\\s+(?:project\\s+)?(?:cost|costs|budget)\\b|\\b(?:cost|costs)\\s+(?:of|for)\\s+(?:construction|the"
                            + " project|the"
                            + " mission)\\b|\\b(?:project|mission|programme|program)\\b.{0,30}\\b(?:cost-capped|budgeted)\\b|\\b(Baukosten|Gesamtkosten|Projektkosten|Gesamtbudget|coût"
                            + " de construction|coût total|coût du projet|budget total|coste de"
                            + " construcción|costo de construcción|coste total|costo total|presupuesto"
                            + " total|custo de construção|custo total|custo do projeto|orçamento total|стоимость"
                            + " строительства|общая стоимость|итоговая стоимость|стоимость проекта|бюджет"
                            + " проекта|costo di costruzione|costo totale|costo del progetto|bouwkosten|totale"
                            + " kosten|projectkosten|koszt budowy|całkowity koszt|koszt projektu|inşaat"
                            + " maliyeti|toplam maliyet|proje maliyeti)\\b");
    private static final Pattern ORIGINAL_MARKER =
            Pattern.compile(
                    "(?iu)\\b(original|initial|originally|initially|ursprünglich|initialement|inicial(?:mente)?|первоначальн(?:ая|ый|ое|о)|inizial(?:e|mente)|oorspronkelijk|początkow[yo]|başlangıçta)\\b");
    private static final Pattern FINAL_MARKER =
            Pattern.compile(
                    "(?iu)\\b(final|actual|eventual|eventually|ultimately|at the end|in the end|am"
                            + " Ende|endgültig|tatsächlich|finalement|réel|efetivo|итогов(?:ая|ый|ое)|фактическ(?:ая|ий|ое)|окончательн(?:ая|ый|ое)|finale|effettivo|uiteindelijk|werkelijk|ostateczn[yae]|rzeczywist[yae]|nihai|gerçek)\\b");
    private static final Pattern ORIGINAL_BUDGET_CONTEXT =
            Pattern.compile(
                    "(?iu)\\b(original|initial|originally|initially|ursprünglich|initialement|inicial(?:mente)?|первоначальн(?:ая|ый|ое|о)|inizial(?:e|mente)|oorspronkelijk|początkow[yo]|başlangıçta)\\b.{0,60}\\b(budget|cost|estimate|Kosten|coût|coste|costo|orçamento|стоимость|бюджет|koszt|maliyet)\\b");
    private static final Pattern BUDGET_CONTEXT =
            Pattern.compile(
                    "(?iu)\\b(budget|budgeted|cost-capped|investment|funding|Gesamtbudget|Investition|budget"
                            + " total|investissement|presupuesto|inversión|orçamento|investimento|бюджет|инвестиц(?:ия|ии|ий)|begroting|budżet|yatırım|bütçe)\\b");
    private static final Pattern FINAL_COST_CONTEXT =
            Pattern.compile(
                    "(?iu)\\b(final|actual|eventual|eventually|ultimately|endgültig|tatsächlich|finalement|réel|real|efetivo|итогов(?:ая|ый|ое)|фактическ(?:ая|ий|ое)|окончательн(?:ая|ый|ое)|finale|effettivo|uiteindelijk|werkelijk|ostateczn[yae]|rzeczywist[yae]|nihai|gerçek)\\b");
    private static final Pattern ESTIMATE_CONTEXT =
            Pattern.compile(
                    "(?iu)\\b(estimate|estimated|projected|forecast|Schätzung|geschätzt|estimé|prévu|estimado|previsto|estimativa|projetado|оцен(?:ка|ивался)|предполагаем(?:ая|ый|ое)|stimato|geraamd|geschat|szacowan[yae]|tahmini|öngörülen)\\b");
    private static final Set<String> SUPPORTED_ARTICLE_LANGUAGES =
            Set.of("en", "de", "fr", "es", "pt", "ru", "it", "nl", "pl", "tr");
    private static final Map<String, String> CURRENCY_CODES = currencyCodes();

    public List<WikipediaCostMatch> extractAll(
            String wikitext, String countryCode, String snapshotUrl) {
        String infobox = WikipediaWikitextParser.firstInfobox(wikitext);
        List<WikipediaInfoboxField> sourceFields = new ArrayList<>();
        sourceFields.addAll(costFields(infobox));
        sourceFields.addAll(articleLeadCostFields(wikitext, infobox));
        return extractFields(sourceFields, wikitext, countryCode, snapshotUrl, true);
    }

    public List<WikipediaCostMatch> extractCitedArticle(
            String wikitext, String language, String countryCode, String snapshotUrl) {
        String normalizedLanguage = language == null ? "en" : language.toLowerCase(Locale.ROOT);
        List<WikipediaInfoboxField> sourceFields = articleBodyCostFields(wikitext, normalizedLanguage);
        return extractFields(sourceFields, wikitext, countryCode, snapshotUrl, false);
    }

    private List<WikipediaCostMatch> extractFields(
            List<WikipediaInfoboxField> sourceFields,
            String wikitext,
            String countryCode,
            String snapshotUrl,
            boolean allowSnapshotReference) {
        List<WikipediaCostMatch> result = new ArrayList<>();
        for (WikipediaInfoboxField field : sourceFields) {
            Optional<String> referenceUrl =
                    referenceUrl(field.value()).or(() -> namedReferenceUrl(field.value(), wikitext));
            if (allowSnapshotReference) {
                referenceUrl =
                        referenceUrl.or(
                                () -> Optional.ofNullable(snapshotUrl).filter(value -> !value.isBlank()));
            }
            if (referenceUrl.isEmpty()) {
                continue;
            }
            List<WikipediaCostMatch> matches = new ArrayList<>();
            ProjectCostKind costKind = costKind(field);
            boolean containsRange =
                    collectCurrencyRanges(field, countryCode, referenceUrl.orElseThrow(), costKind, matches);
            if (!containsRange) {
                collectMatches(
                        field, countryCode, referenceUrl.orElseThrow(), costKind, AMOUNT, false, matches);
                collectMatches(
                        field,
                        countryCode,
                        referenceUrl.orElseThrow(),
                        costKind,
                        AMOUNT_BEFORE_CURRENCY,
                        true,
                        matches);
                collectMatches(
                        field,
                        countryCode,
                        referenceUrl.orElseThrow(),
                        costKind,
                        LOCALIZED_AMOUNT_BEFORE_CURRENCY,
                        true,
                        matches);
            }
            collectGenericCurrencyTemplates(
                    field, countryCode, referenceUrl.orElseThrow(), costKind, matches);
            matches.stream()
                    .filter(match -> match.amountLow().signum() > 0)
                    .filter(match -> match.amountHigh().compareTo(match.amountLow()) >= 0)
                    .forEach(result::add);
        }
        return result.stream()
                .sorted(
                        Comparator.comparing(WikipediaCostMatch::fieldName)
                                .thenComparing(WikipediaCostMatch::currency)
                                .thenComparing(WikipediaCostMatch::amountLow)
                                .thenComparing(WikipediaCostMatch::amountHigh))
                .distinct()
                .toList();
    }

    private List<WikipediaInfoboxField> articleBodyCostFields(String wikitext, String language) {
        if (wikitext == null || wikitext.isBlank() || !SUPPORTED_ARTICLE_LANGUAGES.contains(language)) {
            return List.of();
        }
        String article = wikitext.replace(WikipediaWikitextParser.firstInfobox(wikitext), " ");
        List<WikipediaInfoboxField> fields = new ArrayList<>();
        int position = 0;
        for (String paragraph : article.split("\\R{2,}")) {
            String trimmed = paragraph.trim();
            if (trimmed.isBlank()
                    || trimmed.startsWith("{{")
                    || trimmed.startsWith("|")
                    || trimmed.startsWith("[[File:")
                    || trimmed.startsWith("[[Category:")) {
                continue;
            }
            for (String sentence : sentencesWithReferencesPreserved(trimmed)) {
                String normalized = normalizeTemplates(sentence);
                boolean hasAmount = hasCurrencyAmount(normalized);
                boolean hasContext = STRICT_WHOLE_PROJECT_COST_CONTEXT.matcher(normalized).find();
                boolean deltaOnly =
                        ARTICLE_DELTA_COST.matcher(normalized).find()
                                && !ARTICLE_TOTAL_COST.matcher(normalized).find();
                boolean invalidScope =
                        PARTIAL_PROJECT_SCOPE.matcher(normalized).find()
                                || COMPARISON_CONTEXT.matcher(normalized).find();
                if (hasCitation(sentence)
                        && hasAmount
                        && hasContext
                        && !deltaOnly
                        && !invalidScope
                        && normalized.length() <= 2500) {
                    Optional<ProjectCostKind> kind = articleCostKind(normalized);
                    if (kind.isPresent()) {
                        fields.add(
                                new WikipediaInfoboxField(
                                        "article_body_"
                                                + language
                                                + "_"
                                                + kind.orElseThrow().name().toLowerCase(Locale.ROOT)
                                                + "_"
                                                + ++position,
                                        sentence));
                    }
                }
            }
        }
        return fields;
    }

    private List<String> sentencesWithReferencesPreserved(String value) {
        Matcher references = CITATION_MARKER.matcher(value);
        StringBuilder protectedText = new StringBuilder();
        List<String> capturedReferences = new ArrayList<>();
        while (references.find()) {
            String token = "WIKIPEDIA_REFERENCE_" + capturedReferences.size();
            capturedReferences.add(references.group());
            references.appendReplacement(protectedText, token);
        }
        references.appendTail(protectedText);
        String markedText =
                protectedText
                        .toString()
                        .replaceAll(
                                "([.!?。！？])(WIKIPEDIA_REFERENCE_[0-9]+)(?=\\s)", "$1$2WIKIPEDIA_SENTENCE_BREAK");
        List<String> sentences =
                Pattern.compile("WIKIPEDIA_SENTENCE_BREAK\\s*|(?<=[.!?。！？])\\s+(?=[\\p{Lu}\\p{Lo}\\[<{])")
                        .splitAsStream(markedText)
                        .map(String::trim)
                        .filter(sentence -> !sentence.isBlank())
                        .toList();
        return sentences.stream()
                .map(sentence -> restoreReferences(sentence, capturedReferences))
                .toList();
    }

    private String restoreReferences(String sentence, List<String> references) {
        String restored = sentence;
        for (int index = 0; index < references.size(); index++) {
            restored = restored.replace("WIKIPEDIA_REFERENCE_" + index, references.get(index));
        }
        return restored;
    }

    private boolean hasCitation(String value) {
        return REFERENCE.matcher(value).find() || NAMED_REFERENCE_TEMPLATE.matcher(value).find();
    }

    private boolean hasCurrencyAmount(String value) {
        return AMOUNT.matcher(value).find()
                || AMOUNT_BEFORE_CURRENCY.matcher(value).find()
                || LOCALIZED_AMOUNT_BEFORE_CURRENCY.matcher(value).find()
                || CURRENCY_RANGE.matcher(value).find()
                || SCALED_CURRENCY_TEMPLATE.matcher(value).find()
                || GENERIC_CURRENCY_TEMPLATE.matcher(value).find();
    }

    private Optional<ProjectCostKind> articleCostKind(String value) {
        if (value.matches("(?s).*\\b(?:revised|updated)\\s+(?:project\\s+)?(?:cost|budget|estimate)\\b.*")) {
            return Optional.of(ProjectCostKind.REVISED_BUDGET);
        }
        if (value.matches("(?s).*\\b(?:spent|expended|expenditure)\\b.{0,80}\\b(?:to date|so far|as of)\\b.*")) {
            return Optional.of(ProjectCostKind.EXPENDITURE_TO_DATE);
        }
        boolean original = ORIGINAL_BUDGET_CONTEXT.matcher(value).find();
        boolean budget = BUDGET_CONTEXT.matcher(value).find();
        boolean finalCost =
                FINAL_COST_CONTEXT.matcher(value).find()
                        && (!budget || ARTICLE_TOTAL_COST.matcher(value).find());
        boolean estimate = ESTIMATE_CONTEXT.matcher(value).find();
        if (original && (finalCost || estimate)) {
            return Optional.empty();
        }
        if (finalCost) {
            return Optional.of(ProjectCostKind.FINAL_COST);
        }
        if (original) {
            return Optional.of(ProjectCostKind.ORIGINAL_BUDGET);
        }
        if (budget) {
            return Optional.of(ProjectCostKind.APPROVED_BUDGET);
        }
        if (estimate) {
            return Optional.of(ProjectCostKind.CURRENT_ESTIMATE);
        }
        return Optional.of(ProjectCostKind.REPORTED_COST);
    }

    private List<WikipediaInfoboxField> articleLeadCostFields(String wikitext, String infobox) {
        if (wikitext == null || wikitext.isBlank()) {
            return List.of();
        }
        int firstSection = wikitext.indexOf("\n==");
        String lead = firstSection < 0 ? wikitext : wikitext.substring(0, firstSection);
        if (!infobox.isBlank()) {
            lead = lead.replace(infobox, " ");
        }
        List<WikipediaInfoboxField> fields = new ArrayList<>();
        int position = 0;
        for (String paragraph : lead.split("\\R{2,}")) {
            String trimmed = paragraph.trim();
            if (trimmed.isBlank() || trimmed.startsWith("{{") || trimmed.startsWith("|")) {
                continue;
            }
            for (String sentence : trimmed.split("(?<=[.!?])\\s+(?=[A-Z])")) {
                String normalized = normalizeTemplates(sentence);
                boolean hasCostContext = ARTICLE_COST_CONTEXT.matcher(normalized).find();
                boolean hasAmount =
                        AMOUNT.matcher(normalized).find()
                                || AMOUNT_BEFORE_CURRENCY.matcher(normalized).find()
                                || CURRENCY_RANGE.matcher(normalized).find();
                boolean deltaOnly =
                        ARTICLE_DELTA_COST.matcher(normalized).find()
                                && !ARTICLE_TOTAL_COST.matcher(normalized).find();
                if (hasCostContext && hasAmount && !deltaOnly && normalized.length() <= 1500) {
                    fields.add(new WikipediaInfoboxField("article_lead_" + ++position, sentence));
                }
            }
        }
        return fields;
    }

    private boolean collectCurrencyRanges(
            WikipediaInfoboxField field,
            String countryCode,
            String referenceUrl,
            ProjectCostKind costKind,
            List<WikipediaCostMatch> matches) {
        String normalized = normalizeTemplates(field.value());
        Matcher matcher = CURRENCY_RANGE.matcher(normalized);
        boolean matched = false;
        while (matcher.find()) {
            String currency = resolveCurrency(matcher.group(1), countryCode);
            String highCurrency =
                    matcher.group(4) == null ? currency : resolveCurrency(matcher.group(4), countryCode);
            String lowScale = matcher.group(3) == null ? matcher.group(6) : matcher.group(3);
            Optional<BigDecimal> low = parseAmount(matcher.group(2), lowScale);
            Optional<BigDecimal> high = parseAmount(matcher.group(5), matcher.group(6));
            if (currency != null
                    && currency.equals(highCurrency)
                    && low.isPresent()
                    && high.isPresent()) {
                ProjectCostKind resolvedKind =
                        costKindForMatch(field, normalized, matcher.start(), costKind);
                matches.add(
                        new WikipediaCostMatch(
                                resolvedKind,
                                low.orElseThrow(),
                                high.orElseThrow(),
                                currency,
                                costYear(field.value()),
                                referenceUrl,
                                compactEvidence(field.value()),
                                field.name(),
                                scopeNote(field)));
                matched = true;
            }
        }
        return matched;
    }

    private List<WikipediaInfoboxField> costFields(String infobox) {
        List<WikipediaInfoboxField> fields = new ArrayList<>();
        StringBuilder current = null;
        String currentName = null;
        int templateDepth = 0;
        for (String line : infobox.split("\\R")) {
            String trimmed = line.trim();
            if (current != null && templateDepth <= 0 && FIELD_START.matcher(trimmed).matches()) {
                fields.add(new WikipediaInfoboxField(currentName, current.toString().trim()));
                current = null;
                currentName = null;
                templateDepth = 0;
            }
            Matcher start = COST_FIELD_START.matcher(trimmed);
            if (current == null && start.matches()) {
                currentName = normalizeFieldName(start.group(1));
                current = new StringBuilder(start.group(2));
                templateDepth = templateDepth(start.group(2));
            } else if (current != null) {
                current.append('\n').append(line);
                templateDepth += templateDepth(line);
            }
        }
        if (current != null) {
            fields.add(new WikipediaInfoboxField(currentName, current.toString().trim()));
        }
        return fields;
    }

    private int templateDepth(String value) {
        int depth = 0;
        for (int index = 0; index < value.length() - 1; index++) {
            String pair = value.substring(index, index + 2);
            if ("{{".equals(pair)) {
                depth++;
                index++;
            } else if ("}}".equals(pair)) {
                depth--;
                index++;
            }
        }
        return depth;
    }

    private void collectMatches(
            WikipediaInfoboxField field,
            String countryCode,
            String referenceUrl,
            ProjectCostKind costKind,
            Pattern pattern,
            boolean amountFirst,
            List<WikipediaCostMatch> matches) {
        String normalized = normalizeTemplates(field.value());
        Matcher matcher = pattern.matcher(normalized);
        while (matcher.find()) {
            String currencyToken = matcher.group(amountFirst ? 3 : 1);
            String amountToken = matcher.group(amountFirst ? 1 : 2);
            String scaleToken = matcher.group(amountFirst ? 2 : 3);
            String currency = resolveCurrency(currencyToken, countryCode);
            ProjectCostKind resolvedKind = costKindForMatch(field, normalized, matcher.start(), costKind);
            parseAmount(amountToken, scaleToken)
                    .map(
                            amount ->
                                    new WikipediaCostMatch(
                                            resolvedKind,
                                            amount,
                                            amount,
                                            currency,
                                            costYear(field.value()),
                                            referenceUrl,
                                            compactEvidence(field.value()),
                                            field.name(),
                                            scopeNote(field)))
                    .filter(match -> match.currency() != null)
                    .ifPresent(matches::add);
        }
    }

    private void collectGenericCurrencyTemplates(
            WikipediaInfoboxField field,
            String countryCode,
            String referenceUrl,
            ProjectCostKind costKind,
            List<WikipediaCostMatch> matches) {
        String normalized = normalizeTemplates(field.value());
        Matcher matcher = GENERIC_CURRENCY_TEMPLATE.matcher(normalized);
        while (matcher.find()) {
            String currency =
                    matcher.group(3) == null
                            ? defaultCurrency(countryCode)
                            : resolveCurrency(matcher.group(3), countryCode);
            ProjectCostKind resolvedKind = costKindForMatch(field, normalized, matcher.start(), costKind);
            parseAmount(matcher.group(1), matcher.group(2))
                    .map(
                            amount ->
                                    new WikipediaCostMatch(
                                            resolvedKind,
                                            amount,
                                            amount,
                                            currency,
                                            costYear(field.value()),
                                            referenceUrl,
                                            compactEvidence(field.value()),
                                            field.name(),
                                            scopeNote(field)))
                    .filter(match -> match.currency() != null)
                    .ifPresent(matches::add);
        }
    }

    private ProjectCostKind costKindForMatch(
            WikipediaInfoboxField field,
            String normalizedValue,
            int amountStart,
            ProjectCostKind fallback) {
        if (!field.name().startsWith("article_body_")) {
            return fallback;
        }
        String precedingContext =
                normalizedValue.substring(Math.max(0, amountStart - 140), amountStart);
        Map<ProjectCostKind, Integer> markers = new LinkedHashMap<>();
        markers.put(ProjectCostKind.ORIGINAL_BUDGET, lastMatchEnd(ORIGINAL_MARKER, precedingContext));
        markers.put(ProjectCostKind.FINAL_COST, lastMatchEnd(FINAL_MARKER, precedingContext));
        markers.put(ProjectCostKind.CURRENT_ESTIMATE, lastMatchEnd(ESTIMATE_CONTEXT, precedingContext));
        markers.put(ProjectCostKind.APPROVED_BUDGET, lastMatchEnd(BUDGET_CONTEXT, precedingContext));
        return markers.entrySet().stream()
                .filter(entry -> entry.getValue() >= 0)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(fallback);
    }

    private int lastMatchEnd(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        int end = -1;
        while (matcher.find()) {
            end = matcher.end();
        }
        return end;
    }

    private Optional<BigDecimal> parseAmount(String value, String scale) {
        String normalized = normalizeNumber(value, scale);
        try {
            BigDecimal multiplier =
                    switch (scale == null ? "" : scale.toLowerCase(Locale.ROOT)) {
                        case "trillion" -> new BigDecimal("1000000000000");
                        case "billion",
                             "billions",
                             "milliard",
                             "milliards",
                             "milliarde",
                             "milliarden",
                             "mil millones",
                             "bilhão",
                             "bilhões",
                             "miliardo",
                             "miliardi",
                             "miljard",
                             "miljarden",
                             "miliard",
                             "miliardy",
                             "miliardów",
                             "milyar",
                             "миллиард",
                             "миллиарда",
                             "миллиардов",
                             "mld",
                             "bn",
                             "b" -> new BigDecimal("1000000000");
                        case "million",
                             "millions",
                             "millionen",
                             "millón",
                             "millones",
                             "milhão",
                             "milhões",
                             "milione",
                             "milioni",
                             "miljoen",
                             "miljoenen",
                             "milion",
                             "miliony",
                             "milionów",
                             "milyon",
                             "миллион",
                             "миллиона",
                             "миллионов",
                             "mln",
                             "mn",
                             "m" -> new BigDecimal("1000000");
                        default -> BigDecimal.ONE;
                    };
            return Optional.of(new BigDecimal(normalized).multiply(multiplier));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private String normalizeNumber(String value, String scale) {
        String normalized = value.replace(" ", "");
        int comma = normalized.lastIndexOf(',');
        int dot = normalized.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            char decimalSeparator = comma > dot ? ',' : '.';
            char groupingSeparator = decimalSeparator == ',' ? '.' : ',';
            return normalized
                    .replace(String.valueOf(groupingSeparator), "")
                    .replace(decimalSeparator, '.');
        }
        if (comma >= 0) {
            int fractionalDigits = normalized.length() - comma - 1;
            boolean decimalScale = scale != null && fractionalDigits > 0 && fractionalDigits <= 2;
            return decimalScale || fractionalDigits != 3
                    ? normalized.replace(',', '.')
                    : normalized.replace(",", "");
        }
        return normalized;
    }

    private Optional<String> referenceUrl(String field) {
        Matcher matcher = REFERENCE_URL.matcher(field.replace("&amp;", "&"));
        while (matcher.find()) {
            String value = matcher.group(1).replaceAll("[.,;]+$", "");
            if (value.startsWith("https://") || value.startsWith("http://")) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private Optional<String> namedReferenceUrl(String field, String wikitext) {
        List<String> names = new ArrayList<>();
        NAMED_REFERENCE.matcher(field).results().map(match -> match.group(1)).forEach(names::add);
        NAMED_REFERENCE_TEMPLATE
                .matcher(field)
                .results()
                .map(match -> match.group(1).trim())
                .forEach(names::add);
        for (String name : names) {
            String referenceName = Pattern.quote(name);
            Pattern definition =
                    Pattern.compile(
                            "(?is)<ref\\s+name\\s*=\\s*[\"']?" + referenceName + "[\"']?[^>]*>(.*?)</ref>");
            Matcher match = definition.matcher(wikitext);
            if (match.find()) {
                Optional<String> resolved = referenceUrl(match.group(1));
                if (resolved.isPresent()) {
                    return resolved;
                }
            }
        }
        return Optional.empty();
    }

    private ProjectCostKind costKind(WikipediaInfoboxField field) {
        String name = field.name();
        String value = field.value().toLowerCase(Locale.ROOT);
        if (name.startsWith("article_body_") && name.contains("_original_budget_")) {
            return ProjectCostKind.ORIGINAL_BUDGET;
        }
        if (name.startsWith("article_body_") && name.contains("_approved_budget_")) {
            return ProjectCostKind.APPROVED_BUDGET;
        }
        if (name.startsWith("article_body_") && name.contains("_final_cost_")) {
            return ProjectCostKind.FINAL_COST;
        }
        if (name.startsWith("article_body_") && name.contains("_current_estimate_")) {
            return ProjectCostKind.CURRENT_ESTIMATE;
        }
        if (name.startsWith("article_body_") && name.contains("_revised_budget_")) {
            return ProjectCostKind.REVISED_BUDGET;
        }
        if (name.startsWith("article_body_") && name.contains("_expenditure_to_date_")) {
            return ProjectCostKind.EXPENDITURE_TO_DATE;
        }
        if (name.startsWith("article_body_") && name.contains("_reported_cost_")) {
            return ProjectCostKind.REPORTED_COST;
        }
        if (name.matches(
                "original_budget|initial_budget|original_cost_estimate|initial_cost_estimate")) {
            return ProjectCostKind.ORIGINAL_BUDGET;
        }
        if (name.equals("approved_budget") || name.equals("budget")) {
            return ProjectCostKind.APPROVED_BUDGET;
        }
        if (name.matches("revised_budget|revised_cost|revised_cost_estimate")) {
            return ProjectCostKind.REVISED_BUDGET;
        }
        if (name.matches("expenditure_to_date|spent_to_date|amount_spent|expenditure")) {
            return ProjectCostKind.EXPENDITURE_TO_DATE;
        }
        if (name.matches("final_cost|actual_cost")
                || value.matches("(?s).*\\b(final|actual)\\s+cost\\b.*")) {
            return ProjectCostKind.FINAL_COST;
        }
        if (value.matches("(?s).*\\b(?:ultimately|eventually)\\s+cost\\b.*")
                || value.matches("(?s).*\\beventual\\s+(?:project\\s+)?cost\\b.*")) {
            return ProjectCostKind.FINAL_COST;
        }
        if (value.matches("(?s).*\\b(?:estimated|approved|allocated)\\s+budget\\b.*")
                || value.matches("(?s).*\\bbudgeted\\s+(?:at|for)\\b.*")) {
            return ProjectCostKind.APPROVED_BUDGET;
        }
        if (name.matches("estimated_cost|current_estimate|cost_estimate")
                || value.matches("(?s).*\\b(estimate|estimated|projected)\\b.*")) {
            return ProjectCostKind.CURRENT_ESTIMATE;
        }
        if (name.matches("financing|funding")) {
            return ProjectCostKind.FINANCING_AMOUNT;
        }
        if (name.equals("contract_value")) {
            return ProjectCostKind.CONTRACT_VALUE;
        }
        if (value.matches("(?s).*\\b(original|initial)\\s+(budget|estimate)\\b.*")) {
            return ProjectCostKind.ORIGINAL_BUDGET;
        }
        if (value.matches(
                "(?s).*\\b(?:originally|initially)\\b.{0,80}\\b(cost|budget|estimate)\\b.*")) {
            return ProjectCostKind.ORIGINAL_BUDGET;
        }
        if (value.matches("(?s).*\\b(?:approved|allocated)\\s+budget\\b.*")
                || value.matches("(?s).*\\b\\d{4}\\s+budget\\b.*")) {
            return ProjectCostKind.APPROVED_BUDGET;
        }
        if (name.startsWith("article_lead_")
                && value.matches("(?s).*\\bbudget(?:ed)?\\b.*")
                && !value.matches("(?s).*\\b(?:final|actual|total)\\s+cost\\b.*")) {
            return ProjectCostKind.APPROVED_BUDGET;
        }
        return ProjectCostKind.REPORTED_COST;
    }

    private String normalizeFieldName(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private Integer costYear(String value) {
        List<Integer> years =
                Pattern.compile("(?<!\\d)(18[5-9]\\d|19\\d{2}|20\\d{2}|21\\d{2})(?!\\d)")
                        .matcher(compactEvidence(value))
                        .results()
                        .map(match -> Integer.parseInt(match.group(1)))
                        .distinct()
                        .toList();
        return years.size() == 1 ? years.getFirst() : null;
    }

    private String scopeNote(WikipediaInfoboxField field) {
        if (field.name().startsWith("article_body_")) {
            return "Whole-project cost stated in a cited Wikipedia article sentence";
        }
        return "Wikipedia infobox field " + field.name();
    }

    private String normalizeTemplates(String value) {
        String withoutReferences =
                value
                        .replaceAll("(?s)<ref[^>]*>.*?</ref>", " ")
                        .replaceAll("(?s)<ref[^>]*/>", " ")
                        .replaceAll("(?i)\\{\\{\\s*r\\s*\\|[^}]+}}", " ");
        return SCALED_CURRENCY_TEMPLATE
                .matcher(withoutReferences)
                .replaceAll("$1 $2 $3")
                .replaceAll("(?i)\\{\\{\\s*Euro\\s*\\|", "EUR ")
                .replaceAll("(?i)\\{\\{\\s*Pound sterling\\s*\\|", "GBP ")
                .replaceAll("(?i)\\{\\{\\s*US\\$\\s*\\|", "US\\$ ")
                .replaceAll("(?i)\\{\\{\\s*(USD|EUR|GBP|JPY|CNY|INR|AUD|CAD|CHF|KRW|AED)\\s*\\|", "$1 ")
                .replace("{{nbsp}}", " ")
                .replace("&nbsp;", " ")
                .replaceAll("\\[\\[[^]|]+\\|([^]]+)]]", "$1")
                .replaceAll("\\[\\[([^]]+)]]", "$1");
    }

    private String resolveCurrency(String token, String countryCode) {
        String normalized = token.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if ("$".equals(normalized) || "¥".equals(normalized)) {
            return defaultCurrency(countryCode);
        }
        return CURRENCY_CODES.get(normalized);
    }

    private String defaultCurrency(String countryCode) {
        return switch (countryCode == null ? "" : countryCode.toUpperCase(Locale.ROOT)) {
            case "US" -> "USD";
            case "CA" -> "CAD";
            case "AU" -> "AUD";
            case "NZ" -> "NZD";
            case "SG" -> "SGD";
            case "HK" -> "HKD";
            case "CN" -> "CNY";
            case "JP" -> "JPY";
            case "RU" -> "RUB";
            case "TR" -> "TRY";
            case "BR" -> "BRL";
            case "IN" -> "INR";
            case "KR" -> "KRW";
            case "AE" -> "AED";
            case "SA" -> "SAR";
            case "MX" -> "MXN";
            case "ZA" -> "ZAR";
            case "PH" -> "PHP";
            case "ID" -> "IDR";
            case "MY" -> "MYR";
            case "TH" -> "THB";
            case "PL" -> "PLN";
            case "CZ" -> "CZK";
            case "HU" -> "HUF";
            case "IL" -> "ILS";
            default -> null;
        };
    }

    private String compactEvidence(String value) {
        String compact =
                value
                        .replaceAll("(?s)<ref[^>]*>.*?</ref>", " ")
                        .replaceAll("(?s)<ref[^>]*/>", " ")
                        .replaceAll("(?i)\\{\\{\\s*r\\s*\\|[^}]+}}", " ")
                        .replaceAll("\\{\\{|}}", "")
                        .replaceAll("\\[\\[|]]", "")
                        .replaceAll("\\s+", " ")
                        .trim();
        return compact.substring(0, Math.min(compact.length(), 500));
    }

    private static Map<String, String> currencyCodes() {
        Map<String, String> currencies = new LinkedHashMap<>();
        List.of(
                        "USD", "EUR", "GBP", "JPY", "CNY", "INR", "AUD", "CAD", "CHF", "KRW", "AED", "SAR",
                        "RUB", "BRL", "HKD", "SGD", "NOK", "SEK", "DKK", "TRY", "ZAR", "MXN", "NZD", "PHP",
                        "IDR", "MYR", "THB", "PLN", "CZK", "HUF", "ILS")
                .forEach(code -> currencies.put(code, code));
        currencies.put("US$", "USD");
        currencies.put("HK$", "HKD");
        currencies.put("CN¥", "CNY");
        currencies.put("AU$", "AUD");
        currencies.put("A$", "AUD");
        currencies.put("NZ$", "NZD");
        currencies.put("SG$", "SGD");
        currencies.put("CA$", "CAD");
        currencies.put("C$", "CAD");
        currencies.put("R$", "BRL");
        currencies.put("RMB", "CNY");
        currencies.put("EURO", "EUR");
        currencies.put("EUROS", "EUR");
        currencies.put("DOLLAR", "USD");
        currencies.put("DOLLARS", "USD");
        currencies.put("USDOLLAR", "USD");
        currencies.put("USDOLLARS", "USD");
        currencies.put("U.S.DOLLAR", "USD");
        currencies.put("U.S.DOLLARS", "USD");
        currencies.put("POUNDSTERLING", "GBP");
        currencies.put("POUNDSSTERLING", "GBP");
        currencies.put("YEN", "JPY");
        currencies.put("YUAN", "CNY");
        currencies.put("RENMINBI", "CNY");
        currencies.put("ROUBLE", "RUB");
        currencies.put("ROUBLES", "RUB");
        currencies.put("RUBLE", "RUB");
        currencies.put("RUBLES", "RUB");
        currencies.put("ЕВРО", "EUR");
        currencies.put("ДОЛЛАР", "USD");
        currencies.put("ДОЛЛАРА", "USD");
        currencies.put("ДОЛЛАРОВ", "USD");
        currencies.put("РУБЛЬ", "RUB");
        currencies.put("РУБЛЯ", "RUB");
        currencies.put("РУБЛЕЙ", "RUB");
        currencies.put("€", "EUR");
        currencies.put("£", "GBP");
        currencies.put("₹", "INR");
        currencies.put("₩", "KRW");
        currencies.put("₽", "RUB");
        currencies.put("₺", "TRY");
        currencies.put("₱", "PHP");
        currencies.put("₪", "ILS");
        currencies.put("د.إ", "AED");
        return Map.copyOf(currencies);
    }
}
