package io.clueterra.api.admin.budgetcontent.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.admin.budgetcontent.dto.BudgetContentBulkReviewRequest;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentBulkReviewResponse;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentCandidateDto;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentCostClaimInput;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentCostDto;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentCursorPage;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentDescriptionSourceInput;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentFacetsResponse;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentImageDto;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentIssue;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentMetricInput;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentMilestoneInput;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentTab;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentUpdateRequest;
import io.clueterra.api.admin.budgetcontent.dto.BudgetContentUpdateResponse;
import io.clueterra.api.admin.budgetcontent.dto.BudgetFacetCountDto;
import io.clueterra.api.admin.budgetcontent.repository.BudgetContentCandidateRow;
import io.clueterra.api.admin.budgetcontent.repository.BudgetContentCursor;
import io.clueterra.api.admin.budgetcontent.repository.BudgetContentFilter;
import io.clueterra.api.admin.budgetcontent.repository.BudgetContentJdbcRepository;
import io.clueterra.api.admin.budgetcontent.repository.BudgetFacetRow;
import io.clueterra.api.exception.ResourceNotFoundException;
import io.clueterra.api.storage.StorageService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BudgetContentReviewService {

    private static final BigDecimal MINIMUM_PLAYABLE_COST = new BigDecimal("1000000");
    private static final BigDecimal MAXIMUM_PLAYABLE_COST = new BigDecimal("1000000000000");
    private static final BigDecimal ONE_HALF = new BigDecimal("0.5");
    private static final Map<String, String> NORMALIZED_METRIC_UNITS = Map.of(
            "LENGTH", "M",
            "MAIN_SPAN", "M",
            "HEIGHT", "M",
            "FLOOR_AREA", "M2",
            "CAPACITY", "SEAT",
            "POWER", "MW",
            "ANNUAL_PASSENGERS", "PASSENGERS_PER_YEAR",
            "STORAGE_VOLUME", "M3",
            "MASS", "KG");

    private final BudgetContentJdbcRepository repository;
    private final BudgetContentCursorCodec cursorCodec;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;

    public BudgetContentCursorPage findCandidates(
            BudgetContentFilter filter, String encodedCursor, int size) {
        BudgetContentCursor cursor = cursorCodec.decode(encodedCursor);
        List<BudgetContentCandidateRow> rows = repository.findPage(filter, cursor, size + 1);
        boolean hasMore = rows.size() > size;
        List<BudgetContentCandidateRow> pageRows =
                hasMore ? List.copyOf(rows.subList(0, size)) : List.copyOf(rows);
        String nextCursor =
                hasMore && !pageRows.isEmpty() ? cursorCodec.encode(cursorFrom(pageRows.getLast())) : null;
        return new BudgetContentCursorPage(
                pageRows.stream().map(this::toDto).toList(), nextCursor, hasMore);
    }

    public BudgetContentCandidateDto findDetail(String wikidataId, String category) {
        return repository
                .findDetail(wikidataId, category)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Budget candidate not found"));
    }

    public BudgetContentFacetsResponse findFacets(BudgetContentTab tab) {
        List<BudgetFacetRow> rows = repository.findFacets(tab);
        Map<String, List<BudgetFacetCountDto>> grouped =
                rows.stream()
                        .collect(
                                Collectors.groupingBy(
                                        BudgetFacetRow::dimension,
                                        Collectors.mapping(this::toFacet, Collectors.toList())));
        long total =
                rows.stream()
                        .filter(row -> "TOTAL".equals(row.dimension()))
                        .mapToLong(BudgetFacetRow::count)
                        .findFirst()
                        .orElse(0);
        return new BudgetContentFacetsResponse(
                total,
                grouped.getOrDefault("STATUS", List.of()),
                grouped.getOrDefault("CATEGORY", List.of()),
                grouped.getOrDefault("COUNTRY", List.of()),
                grouped.getOrDefault("DIFFICULTY", List.of()),
                grouped.getOrDefault("REGION", List.of()),
                grouped.getOrDefault("ISSUE", List.of()),
                grouped.getOrDefault("QUESTION_STATUS", List.of()),
                grouped.getOrDefault("CHALLENGE_STATUS", List.of()));
    }

    public BudgetContentBulkReviewResponse reviewSelected(
            BudgetContentBulkReviewRequest request, String reviewedBy) {
        return repository.reviewCandidates(
                request.candidates(), request.status(), request.note(), reviewedBy);
    }

    public BudgetContentUpdateResponse updateCandidate(
            String wikidataId, String category, BudgetContentUpdateRequest request, String editedBy) {
        validateEdit(request);
        return repository.updateCandidate(wikidataId, category, request, editedBy);
    }

    private void validateEdit(BudgetContentUpdateRequest request) {
        long answers = request.costClaims().stream().filter(BudgetContentCostClaimInput::answer).count();
        if (answers != 1 || request.costClaims().stream().anyMatch(claim -> claim.answer() && !claim.primary())) {
            throw new IllegalArgumentException("Exactly one primary cost claim must be the playable answer");
        }
        if (request.costClaims().stream().anyMatch(claim ->
                claim.sourceAmountHigh().compareTo(claim.sourceAmountLow()) < 0
                        || claim.amountUsdHigh().compareTo(claim.amountUsdLow()) < 0)) {
            throw new IllegalArgumentException("Cost range maximum cannot be below its minimum");
        }
        var answer = request.costClaims().stream().filter(BudgetContentCostClaimInput::answer).findFirst().orElseThrow();
        BigDecimal answerMidpoint =
                answer.amountUsdLow().add(answer.amountUsdHigh()).multiply(ONE_HALF);
        if (answerMidpoint.compareTo(MINIMUM_PLAYABLE_COST) < 0
                || answerMidpoint.compareTo(MAXIMUM_PLAYABLE_COST) > 0) {
            throw new IllegalArgumentException("Playable cost must be between USD 1 million and USD 1 trillion");
        }
        boolean duplicatePrimaryCost = request.costClaims().stream()
                .filter(BudgetContentCostClaimInput::primary)
                .collect(Collectors.groupingBy(BudgetContentCostClaimInput::costKind, Collectors.counting()))
                .values().stream().anyMatch(count -> count > 1);
        boolean duplicatePrimaryMetric = request.metrics().stream()
                .filter(BudgetContentMetricInput::primary)
                .collect(Collectors.groupingBy(BudgetContentMetricInput::metricType, Collectors.counting()))
                .values().stream().anyMatch(count -> count > 1);
        boolean duplicatePrimaryMilestone = request.milestones().stream()
                .filter(BudgetContentMilestoneInput::primary)
                .collect(Collectors.groupingBy(
                        BudgetContentMilestoneInput::milestoneType, Collectors.counting()))
                .values().stream().anyMatch(count -> count > 1);
        if (duplicatePrimaryCost || duplicatePrimaryMetric || duplicatePrimaryMilestone) {
            throw new IllegalArgumentException("Only one primary claim is allowed for each fact type");
        }
        if (request.metrics().stream().anyMatch(metric ->
                !metric.normalizedUnit().equals(NORMALIZED_METRIC_UNITS.get(metric.metricType())))) {
            throw new IllegalArgumentException("A metric uses an invalid normalized unit");
        }
        if (request.milestones().stream().anyMatch(this::invalidMilestone)) {
            throw new IllegalArgumentException("Milestone value must match DAY or YEAR precision");
        }
    }

    private boolean invalidMilestone(BudgetContentMilestoneInput milestone) {
        return "DAY".equals(milestone.datePrecision())
                ? milestone.milestoneDate() == null || milestone.milestoneYear() != null
                : milestone.milestoneYear() == null || milestone.milestoneDate() != null;
    }

    private BudgetContentCandidateDto toDto(BudgetContentCandidateRow row) {
        return new BudgetContentCandidateDto(
                row.wikidataId(),
                row.category(),
                row.name(),
                row.description(),
                row.countryCode(),
                row.countryName(),
                row.regionCode(),
                row.projectYear(),
                row.difficulty(),
                row.sitelinkCount(),
                row.monthlyPageviews(),
                row.interestScore(),
                row.qualityScore(),
                row.sourceRank(),
                row.qualityStatus(),
                row.evidenceTier(),
                row.costConfidenceScore(),
                qualityFlags(row),
                row.sourceUrl(),
                row.reviewStatus(),
                row.reviewNote(),
                row.reviewedBy(),
                row.reviewedAt(),
                row.fetchedAt(),
                row.duplicateNameCount(),
                row.duplicateImageCount(),
                issues(row),
                image(row),
                cost(row),
                descriptionSource(row.sourcePayload()),
                payloadValues(row.sourcePayload(), "costClaims", BudgetContentCostClaimInput.class),
                payloadValues(row.sourcePayload(), "metrics", BudgetContentMetricInput.class),
                payloadValues(row.sourcePayload(), "milestones", BudgetContentMilestoneInput.class));
    }

    private BudgetContentDescriptionSourceInput descriptionSource(JsonNode payload) {
        if (payload == null || !payload.path("descriptionSource").isObject()) {
            return null;
        }
        return objectMapper.convertValue(
                payload.path("descriptionSource"), BudgetContentDescriptionSourceInput.class);
    }

    private <T> List<T> payloadValues(JsonNode payload, String field, Class<T> valueType) {
        if (payload == null || !payload.path(field).isArray()) {
            return List.of();
        }
        return objectMapper.convertValue(
                payload.path(field),
                objectMapper.getTypeFactory().constructCollectionType(List.class, valueType));
    }

    private BudgetContentImageDto image(BudgetContentCandidateRow row) {
        return new BudgetContentImageDto(
                storageService.resolveBudgetCandidateAssetUrl(
                        row.wikidataId(), row.category(), row.imageSourceSha1(), row.imageObjectKey()),
                row.imageOriginalUrl(),
                row.imageSourcePageUrl(),
                row.imageFileTitle(),
                row.imageAuthorName(),
                row.imageCredit(),
                row.imageLicenseLabel(),
                row.imageLicenseUrl(),
                row.imageDescription(),
                row.imageMimeType(),
                row.imageSourceBytes(),
                row.imageWidth(),
                row.imageHeight(),
                row.imageQualityScore());
    }

    private BudgetContentCostDto cost(BudgetContentCandidateRow row) {
        if (row.answerCostUsd() == null) {
            return null;
        }
        return new BudgetContentCostDto(
                row.questionId(),
                row.answerCostUsd(),
                row.sourceAmount(),
                row.sourceCurrency(),
                row.costYear(),
                row.normalizationNote(),
                row.costSourceName(),
                row.costSourceUrl(),
                row.costReferenceUrl(),
                row.costSnapshotAt());
    }

    private List<BudgetContentIssue> issues(BudgetContentCandidateRow row) {
        List<BudgetContentIssue> issues = new ArrayList<>();
        addIf(issues, row.duplicateNameCount() > 1, BudgetContentIssue.DUPLICATE_NAME);
        addIf(issues, row.duplicateImageCount() > 1, BudgetContentIssue.DUPLICATE_IMAGE);
        addIf(issues, suspiciousName(row.name()), BudgetContentIssue.SUSPICIOUS_NAME);
        addIf(
                issues,
                row.description() == null || row.description().trim().length() < 60,
                BudgetContentIssue.MISSING_DESCRIPTION);
        addIf(issues, row.qualityScore() < 50, BudgetContentIssue.LOW_QUALITY);
        addIf(issues, hasQualityFlag(row, "CATEGORY_COST_OUTLIER"), BudgetContentIssue.COST_OUTLIER);
        addIf(issues, hasAnyQualityFlag(row), BudgetContentIssue.WEAK_COST_EVIDENCE);
        addIf(
                issues,
                "NEEDS_REVIEW".equals(row.qualityStatus()) && !isApproved(row.reviewStatus()),
                BudgetContentIssue.COST_REVIEW_REQUIRED);
        return List.copyOf(issues);
    }

    private List<String> qualityFlags(BudgetContentCandidateRow row) {
        if (row.qualityFlags() == null || !row.qualityFlags().isArray()) {
            return List.of();
        }
        List<String> flags = new ArrayList<>();
        row.qualityFlags().forEach(value -> flags.add(value.asText()));
        return List.copyOf(flags);
    }

    private boolean hasAnyQualityFlag(BudgetContentCandidateRow row) {
        return Stream.of(
                        "LEAD_ONLY_COST",
                        "LOW_AUTHORITY_COST",
                        "PROJECT_PART_COST",
                        "CURRENCY_ERA_MISMATCH",
                        "COST_SOURCE_CONFLICT")
                .anyMatch(flag -> hasQualityFlag(row, flag));
    }

    private boolean hasQualityFlag(BudgetContentCandidateRow row, String flag) {
        return qualityFlags(row).contains(flag);
    }

    private boolean isApproved(String reviewStatus) {
        return "APPROVED".equals(reviewStatus) || "EDITORIAL_APPROVED".equals(reviewStatus);
    }

    private void addIf(List<BudgetContentIssue> issues, boolean condition, BudgetContentIssue issue) {
        if (condition) {
            issues.add(issue);
        }
    }

    private boolean suspiciousName(String name) {
        if (name == null) {
            return true;
        }
        return name.matches("(?i).*(unnamed|unknown|energy project|project\\s*\\(?[0-9]+\\)?).*");
    }

    private BudgetContentCursor cursorFrom(BudgetContentCandidateRow row) {
        return new BudgetContentCursor(
                row.qualityScore(), row.monthlyPageviews(), row.wikidataId(), row.category());
    }

    private BudgetFacetCountDto toFacet(BudgetFacetRow row) {
        return new BudgetFacetCountDto(row.key(), row.label(), row.count());
    }
}
