package io.clueterra.api.admin.budgetcontent.service;

import static io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoValidationSeverity.ERROR;
import static io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoValidationSeverity.WARNING;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoCandidateDto;
import io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoDraftDto;
import io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoFactInput;
import io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoPageDto;
import io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoPageRequest;
import io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoReferenceInput;
import io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoStatus;
import io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoValidationIssue;
import io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoValidationResult;
import io.clueterra.api.admin.budgetcontent.dto.BudgetProjectSeoValidationSeverity;
import io.clueterra.api.admin.budgetcontent.repository.BudgetProjectSeoEditorialContext;
import io.clueterra.api.admin.budgetcontent.repository.BudgetProjectSeoJdbcRepository;
import io.clueterra.api.common.dto.PageResponse;
import io.clueterra.api.exception.ResourceConflictException;
import io.clueterra.api.common.utils.SlugUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BudgetProjectSeoService {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final BigDecimal ONE_BILLION = new BigDecimal("1000000000");
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");

    private final BudgetProjectSeoJdbcRepository repository;

    public PageResponse<BudgetProjectSeoPageDto> findPage(
            int page,
            int size,
            String search,
            String publicationStatus) {
        String normalizedSearch = normalizedSearch(search);
        String normalizedStatus = normalizeUpper(publicationStatus);
        long totalElements = repository.count(normalizedSearch, normalizedStatus);
        int totalPages = (int) Math.ceilDiv(totalElements, size);
        List<BudgetProjectSeoPageDto> content = repository.findPage(
                normalizedSearch,
                normalizedStatus,
                size,
                Math.multiplyExact(page, size));
        return new PageResponse<>(content, totalElements, totalPages, page, size);
    }

    public PageResponse<BudgetProjectSeoCandidateDto> findCandidates(
            int page,
            int size,
            String search,
            String pageStatus) {
        String normalizedSearch = normalizedSearch(search);
        String normalizedStatus = normalizeUpper(pageStatus);
        long totalElements = repository.countCandidates(normalizedSearch, normalizedStatus);
        int totalPages = (int) Math.ceilDiv(totalElements, size);
        List<BudgetProjectSeoCandidateDto> content = repository.findCandidates(
                normalizedSearch,
                normalizedStatus,
                size,
                Math.multiplyExact(page, size));
        return new PageResponse<>(content, totalElements, totalPages, page, size);
    }

    public BudgetProjectSeoDraftDto suggestDraft(String wikidataId) {
        BudgetProjectSeoEditorialContext context = repository.findEditorialContext(wikidataId)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "The reviewed project is not available for publication"));
        String slug = uniqueSlug(context.projectName(), wikidataId);
        String title = fitTitle(context.projectName());
        String costQuestion = "How much did " + context.projectName() + " cost?";
        String answer = formatMoney(context.sourceAmount(), context.sourceCurrency());
        String category = categoryLabel(context.category()).toLowerCase(Locale.ROOT);
        String year = context.projectYear() == null ? "" : " completed or launched in " + context.projectYear();
        String intro = context.projectName() + " is a " + category + " in " + context.countryName() + year
                + ". " + context.description() + " The cost below is shown with the year and scope stated by its source.";
        String answerDetail = "The cited source reports " + answer + " for " + context.projectName()
                + ". The original currency, cost basis and source remain attached to the figure.";
        String scope = hasText(context.costScopeNote())
                ? context.costScopeNote()
                : "The amount represents the reported project cost in the cited source. It should be read using that source's scope rather than as a current replacement value or an automatically inflation-adjusted estimate.";
        if (scope.length() < 100) {
            scope += " Later operating costs, replacement values and unrelated programme spending are not included unless the source says otherwise.";
        }
        String methodology = hasText(context.normalizationNote())
                ? context.normalizationNote()
                : "The cited amount remains in " + context.sourceCurrency()
                        + ", alongside the historical US-dollar comparison used by the game. No present-day inflation adjustment is applied.";
        if (methodology.length() < 100) {
            methodology += " The value is not silently restated as a current-price estimate.";
        }
        String metaDescription = fitDescription(
                "How much did " + context.projectName() + " cost? Explore the reported " + answer
                        + " figure, what it includes, project facts, timeline and original sources.");
        String keywords = context.projectName() + " cost, how much did " + context.projectName()
                + " cost, " + category + " cost, megaproject costs";
        return new BudgetProjectSeoDraftDto(
                slug,
                costQuestion,
                title,
                metaDescription,
                keywords,
                intro,
                answerDetail,
                scope,
                context.description(),
                methodology,
                context.costSourceName(),
                context.costSourceUrl(),
                List.of(),
                List.of());
    }

    public BudgetProjectSeoValidationResult validate(
            String wikidataId,
            BudgetProjectSeoPageRequest request) {
        List<BudgetProjectSeoValidationIssue> issues = new ArrayList<>();
        BudgetProjectSeoEditorialContext context = repository.findEditorialContext(wikidataId).orElse(null);
        if (context == null) {
            error(issues, "PROJECT_UNAVAILABLE", "project", "The reviewed project is missing or inactive.");
        } else {
            validateProjectData(context, issues);
        }
        validateEditorialFields(wikidataId, request, context, issues);
        long errorCount = issues.stream().filter(issue -> issue.severity() == ERROR).count();
        long warningCount = issues.stream().filter(issue -> issue.severity() == WARNING).count();
        return new BudgetProjectSeoValidationResult(
                errorCount == 0,
                errorCount,
                warningCount,
                List.copyOf(issues));
    }

    public BudgetProjectSeoPageDto save(
            String wikidataId,
            BudgetProjectSeoPageRequest request,
            String updatedBy) {
        if (request.publicationStatus() == BudgetProjectSeoStatus.PUBLISHED) {
            BudgetProjectSeoValidationResult validation = validate(wikidataId, request);
            if (!validation.publishable()) {
                String firstError = validation.issues().stream()
                        .filter(issue -> issue.severity() == ERROR)
                        .map(BudgetProjectSeoValidationIssue::message)
                        .findFirst()
                        .orElse("The project page is not ready to publish.");
                throw new ResponseStatusException(
                        BAD_REQUEST,
                        firstError + " Fix " + validation.errorCount() + " publication error(s) first.");
            }
        }
        try {
            return repository.upsert(wikidataId, request, updatedBy)
                    .orElseThrow(() -> new ResponseStatusException(
                            BAD_REQUEST,
                            "The project page does not meet the publication requirements"));
        } catch (DataIntegrityViolationException exception) {
            throw new ResourceConflictException("The project page slug is already in use");
        }
    }

    private void validateProjectData(
            BudgetProjectSeoEditorialContext context,
            List<BudgetProjectSeoValidationIssue> issues) {
        if (!context.imageAvailable()) {
            error(issues, "IMAGE_MISSING", "project", "Add a reviewed project image before publishing.");
        } else if (!context.imageMetadataComplete()) {
            error(issues, "IMAGE_INCOMPLETE", "project", "Complete the image attribution, alt text and minimum dimensions.");
        }
        if (!context.primaryCostAvailable()) {
            error(issues, "PRIMARY_COST_MISSING", "project", "Select a primary reviewed cost claim before publishing.");
        } else if ("DISPUTED".equals(context.costVerificationStatus())) {
            error(issues, "PRIMARY_COST_DISPUTED", "project", "The primary cost claim is disputed and cannot be published.");
        }
        if (context.costConfidenceScore() < 70) {
            warning(issues, "LOW_COST_CONFIDENCE", "project", "Cost confidence is below 70; verify the evidence before publishing.");
        }
        if (List.of("UNREFERENCED_KNOWLEDGE_BASE", "ENCYCLOPEDIA_SNAPSHOT", "ENCYCLOPEDIA_LEAD")
                .contains(context.costEvidenceTier())) {
            error(issues, "WEAK_COST_EVIDENCE", "project", "Publishing requires an official, referenced or directly cited cost source.");
        } else if ("CITED_ENCYCLOPEDIA".equals(context.costEvidenceTier())) {
            warning(issues, "SECONDARY_COST_EVIDENCE", "project", "The claim is directly cited, but an original publisher or official source would make the page stronger.");
        }
        if (context.hasQualityFlags()) {
            warning(issues, "QUALITY_FLAGS", "project", "The reviewed project still has quality flags. Check them in Content Studio.");
        }
        if (!hasText(context.costEvidenceText())) {
            warning(issues, "EVIDENCE_TEXT_MISSING", "project", "The primary cost claim has no evidence excerpt.");
        }
        if (!hasText(context.costScopeNote())) {
            warning(issues, "SCOPE_NOTE_MISSING", "project", "The primary cost claim has no structured scope note.");
        }
        if (textLength(context.description()) < 120) {
            warning(issues, "SHORT_PROJECT_DESCRIPTION", "project", "The canonical project description is short; verify the page context carefully.");
        }
        if (context.metricCount() + context.milestoneCount() == 0) {
            warning(issues, "FACTS_MISSING", "project", "No metrics or milestones are available for the project facts panel.");
        }
        if (context.projectYear() == null) {
            warning(issues, "PROJECT_YEAR_MISSING", "project", "The project year is missing.");
        }
        if (context.costYear() == null) {
            warning(issues, "COST_YEAR_MISSING", "project", "The cost basis year is missing.");
        }
    }

    private void validateEditorialFields(
            String wikidataId,
            BudgetProjectSeoPageRequest request,
            BudgetProjectSeoEditorialContext context,
            List<BudgetProjectSeoValidationIssue> issues) {
        validateRequiredLength(issues, "SLUG_REQUIRED", "slug", request.slug(), 1, 120, "Slug");
        if (hasText(request.slug()) && !SLUG_PATTERN.matcher(request.slug().trim()).matches()) {
            error(issues, "SLUG_INVALID", "slug", "Use lowercase letters, numbers and single hyphens in the slug.");
        }
        if (repository.slugOwnedByAnother(wikidataId, normalized(request.slug()))) {
            error(issues, "SLUG_TAKEN", "slug", "This slug is already used by another project page.");
        }
        validateRequiredLength(issues, "COST_QUESTION_LENGTH", "costQuestion", request.costQuestion(), 20, 240, "Cost question");
        if (hasText(request.costQuestion()) && !request.costQuestion().trim().endsWith("?")) {
            warning(issues, "COST_QUESTION_PUNCTUATION", "costQuestion", "Use a complete question ending in a question mark.");
        }
        validateRequiredLength(issues, "TITLE_LENGTH", "seoTitle", request.seoTitle(), 20, 90, "SEO title");
        validateRequiredLength(issues, "DESCRIPTION_LENGTH", "metaDescription", request.metaDescription(), 70, 220, "Meta description");
        validateRequiredLength(issues, "KEYWORDS_REQUIRED", "keywords", request.keywords(), 1, 600, "Keywords");
        validateRequiredLength(issues, "INTRO_LENGTH", "intro", request.intro(), 120, 4000, "Introduction");
        validateRequiredLength(issues, "ANSWER_LENGTH", "answerDetail", request.answerDetail(), 120, 4000, "Answer explanation");
        validateRequiredLength(issues, "SCOPE_LENGTH", "scopeText", request.scopeText(), 100, 4000, "Cost scope");
        validateRequiredLength(issues, "CONTEXT_LENGTH", "contextText", request.contextText(), 120, 4000, "Project context");
        validateRequiredLength(issues, "METHODOLOGY_LENGTH", "methodologyText", request.methodologyText(), 100, 4000, "Methodology");
        validateRequiredLength(issues, "SOURCE_NAME_REQUIRED", "sourceName", request.sourceName(), 1, 240, "Source name");
        validateRequiredLength(issues, "SOURCE_URL_REQUIRED", "sourceUrl", request.sourceUrl(), 1, 2000, "Source URL");
        if (hasText(request.sourceUrl()) && !isHttpUrl(request.sourceUrl())) {
            error(issues, "SOURCE_URL_INVALID", "sourceUrl", "Use a complete http:// or https:// source URL.");
        } else if (hasText(request.sourceName()) && sourceLabelContradictsUrl(
                request.sourceName(), request.sourceUrl())) {
            error(
                    issues,
                    "SOURCE_NAME_URL_MISMATCH",
                    "sourceName",
                    "The source name says Wikipedia or Wikidata, but the URL opens another publisher. Use the publisher or domain shown by the URL.");
        }
        validateReferences(request.references(), issues);
        validateFacts(request.facts(), issues);

        String combinedCopy = String.join(" ",
                normalized(request.intro()),
                normalized(request.answerDetail()),
                normalized(request.scopeText()),
                normalized(request.contextText()),
                normalized(request.methodologyText())).toLowerCase(Locale.ROOT);
        if (combinedCopy.contains("this source-backed clueterra guide")
                || combinedCopy.contains("this is the source-backed figure used by budgetguessr")) {
            error(issues, "GENERIC_TEMPLATE_COPY", "content", "Replace the generic generated copy with project-specific editorial text.");
        }

        int titleLength = textLength(request.seoTitle());
        if (titleLength > 65 && titleLength <= 90) {
            warning(issues, "TITLE_MAY_TRUNCATE", "seoTitle", "The title may be truncated in search results; aim for 50–65 characters.");
        }
        int descriptionLength = textLength(request.metaDescription());
        if (descriptionLength >= 70 && descriptionLength < 120) {
            warning(issues, "DESCRIPTION_SHORT", "metaDescription", "The description is valid but short; 120–160 characters usually uses the snippet better.");
        } else if (descriptionLength > 160 && descriptionLength <= 220) {
            warning(issues, "DESCRIPTION_MAY_TRUNCATE", "metaDescription", "The description may be truncated; aim for 120–160 characters.");
        }
        if (context != null && hasText(request.seoTitle())
                && !request.seoTitle().toLowerCase(Locale.ROOT).contains(context.projectName().toLowerCase(Locale.ROOT))) {
            warning(issues, "TITLE_MISSING_PROJECT_NAME", "seoTitle", "Include the project name in the SEO title where possible.");
        }
    }

    private void validateReferences(
            List<BudgetProjectSeoReferenceInput> references,
            List<BudgetProjectSeoValidationIssue> issues) {
        for (int index = 0; index < references.size(); index++) {
            BudgetProjectSeoReferenceInput reference = references.get(index);
            String field = "references[" + index + "]";
            if (!isHttpUrl(reference.url())) {
                error(issues, "REFERENCE_URL_INVALID", field, "Supporting references need a complete http:// or https:// URL.");
            } else if (sourceLabelContradictsUrl(reference.name(), reference.url())) {
                error(
                        issues,
                        "REFERENCE_NAME_URL_MISMATCH",
                        field,
                        "The reference name says Wikipedia or Wikidata, but its URL opens another publisher.");
            }
        }
        long distinctUrls = references.stream()
                .map(BudgetProjectSeoReferenceInput::url)
                .map(this::normalized)
                .distinct()
                .count();
        if (distinctUrls != references.size()) {
            error(issues, "REFERENCE_URL_DUPLICATE", "references", "Remove duplicate supporting source URLs.");
        }
    }

    private void validateFacts(
            List<BudgetProjectSeoFactInput> facts,
            List<BudgetProjectSeoValidationIssue> issues) {
        for (int index = 0; index < facts.size(); index++) {
            BudgetProjectSeoFactInput fact = facts.get(index);
            if (!isHttpUrl(fact.sourceUrl())) {
                error(issues, "FACT_SOURCE_URL_INVALID", "facts[" + index + "]", "Every additional fact needs a complete source URL.");
            } else if (sourceLabelContradictsUrl(fact.sourceName(), fact.sourceUrl())) {
                error(
                        issues,
                        "FACT_SOURCE_NAME_URL_MISMATCH",
                        "facts[" + index + "]",
                        "The fact source name says Wikipedia or Wikidata, but its URL opens another publisher.");
            }
        }
    }

    private boolean sourceLabelContradictsUrl(String sourceName, String sourceUrl) {
        if (!hasText(sourceName) || !isHttpUrl(sourceUrl)) {
            return false;
        }
        String normalizedName = sourceName.trim().toLowerCase(Locale.ROOT);
        String host = URI.create(sourceUrl.trim()).getHost().toLowerCase(Locale.ROOT);
        return (normalizedName.contains("wikipedia") && !matchesHost(host, "wikipedia.org"))
                || (normalizedName.contains("wikidata") && !matchesHost(host, "wikidata.org"));
    }

    private boolean matchesHost(String host, String expectedHost) {
        return host.equals(expectedHost) || host.endsWith("." + expectedHost);
    }

    private void validateRequiredLength(
            List<BudgetProjectSeoValidationIssue> issues,
            String code,
            String field,
            String value,
            int minimum,
            int maximum,
            String label) {
        int length = textLength(value);
        if (length < minimum) {
            error(issues, code, field, label + " needs at least " + minimum + " characters (currently " + length + ").");
        } else if (length > maximum) {
            error(issues, code, field, label + " must be no longer than " + maximum + " characters (currently " + length + ").");
        }
    }

    private void error(List<BudgetProjectSeoValidationIssue> issues, String code, String field, String message) {
        issue(issues, code, ERROR, field, message);
    }

    private void warning(List<BudgetProjectSeoValidationIssue> issues, String code, String field, String message) {
        issue(issues, code, WARNING, field, message);
    }

    private void issue(
            List<BudgetProjectSeoValidationIssue> issues,
            String code,
            BudgetProjectSeoValidationSeverity severity,
            String field,
            String message) {
        issues.add(new BudgetProjectSeoValidationIssue(code, severity, field, message));
    }

    private String uniqueSlug(String name, String wikidataId) {
        String base = slugify(name);
        if (!repository.slugOwnedByAnother(wikidataId, base)) {
            return base;
        }
        return fitSlug(base, 120 - wikidataId.length() - 1) + "-" + wikidataId.toLowerCase(Locale.ROOT);
    }

    private String slugify(String value) {
        String slug = SlugUtils.slugify(value);
        return fitSlug(slug.isBlank() ? "project" : slug, 120);
    }

    private String fitTitle(String projectName) {
        String title = "How much did " + projectName + " cost? | Clueterra";
        if (title.length() <= 90) {
            return title;
        }
        return fit(projectName, 65) + " project cost | Clueterra";
    }

    private String fitDescription(String value) {
        return value.length() <= 220 ? value : fit(value, 219) + ".";
    }

    private String fit(String value, int maximum) {
        if (value.length() <= maximum) {
            return value;
        }
        int breakAt = value.lastIndexOf(' ', maximum);
        return value.substring(0, breakAt > 0 ? breakAt : maximum).stripTrailing();
    }

    private String formatMoney(BigDecimal amount, String currency) {
        String prefix = switch (currency) {
            case "USD" -> "$";
            case "EUR" -> "€";
            case "GBP" -> "£";
            case "JPY" -> "¥";
            default -> currency + " ";
        };
        if (amount.compareTo(ONE_BILLION) >= 0) {
            return prefix + amount.divide(ONE_BILLION, 4, RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString() + " billion";
        }
        if (amount.compareTo(ONE_MILLION) >= 0) {
            return prefix + amount.divide(ONE_MILLION, 4, RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString() + " million";
        }
        return prefix + amount.stripTrailingZeros().toPlainString();
    }

    private String fitSlug(String value, int maximum) {
        String fitted = value.length() <= maximum ? value : value.substring(0, maximum);
        return fitted.replaceAll("-+$", "");
    }

    private String categoryLabel(String category) {
        return switch (category) {
            case "METRO_RAIL" -> "railway project";
            case "SPACE" -> "space mission";
            case "ENERGY" -> "energy project";
            case "BRIDGE" -> "bridge";
            case "SKYSCRAPER" -> "skyscraper";
            case "STADIUM" -> "stadium";
            case "AIRPORT" -> "airport";
            case "TUNNEL" -> "tunnel";
            case "DAM" -> "dam";
            default -> "major project";
        };
    }

    private boolean isHttpUrl(String value) {
        try {
            URI uri = new URI(value.trim());
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private String normalizedSearch(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int textLength(String value) {
        return value == null ? 0 : value.trim().length();
    }
}
