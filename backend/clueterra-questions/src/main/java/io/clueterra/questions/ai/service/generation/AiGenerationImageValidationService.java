package io.clueterra.questions.ai.service.generation;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.service.Result;
import io.clueterra.questions.ai.model.generation.AiGeneratedImageKind;
import io.clueterra.questions.ai.model.generation.AiGenerationCountryGapCandidate;
import io.clueterra.questions.ai.model.generation.AiGenerationImageAssessment;
import io.clueterra.questions.ai.model.generation.AiGenerationImageValidationBatchResult;
import io.clueterra.questions.ai.model.generation.AiGenerationImageValidationItemResult;
import io.clueterra.questions.ai.model.generation.AiGenerationResolvedImageCandidate;
import io.clueterra.questions.ai.model.generation.AiHintGenerationBatchResult;
import io.clueterra.questions.ai.model.generation.AiHintGenerationProposalResult;
import io.clueterra.questions.ai.model.run.AiRun;
import io.clueterra.questions.ai.model.run.AiRunStage;
import io.clueterra.questions.ai.service.support.AiStageExecutionSupport;
import io.clueterra.questions.ai.service.support.AiStructuredOutputParser;
import io.clueterra.questions.clients.common.ImageRightsSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "clueterra.ai", name = "enabled", havingValue = "true")
public class AiGenerationImageValidationService {

    private final AiWikimediaImageResolver imageResolver;
    private final AiGenerationModelGateway modelGateway;
    private final AiStructuredOutputParser structuredOutputParser;
    private final AiStageExecutionSupport stageExecutionSupport;
    private final AiGenerationImagePolicy imagePolicy;

    public Map<Integer, AiGenerationImageAssessment> validate(
            List<AiGenerationCountryGapCandidate> countries,
            AiHintGenerationBatchResult batchResult,
            AiRun run,
            AiRunStage stage,
            AtomicInteger requestIndex,
            AtomicLong dispatchedInputTokens,
            AtomicLong dispatchedOutputTokens,
            AtomicInteger dispatchedToolCalls) {
        List<AiHintGenerationProposalResult> proposals = safeProposals(batchResult);
        Map<Long, AiGenerationCountryGapCandidate> countriesById = countries.stream()
                .collect(Collectors.toMap(AiGenerationCountryGapCandidate::countryId, Function.identity()));
        Map<Integer, AiGenerationImageAssessment> assessments = new LinkedHashMap<>();
        List<AiGenerationResolvedImageCandidate> candidates = new ArrayList<>();

        for (int proposalIndex = 0; proposalIndex < proposals.size(); proposalIndex++) {
            AiHintGenerationProposalResult proposal = proposals.get(proposalIndex);
            if (!hasText(proposal.imageSourceUrl())) {
                continue;
            }

            AiGenerationCountryGapCandidate country = countriesById.get(proposal.countryId());
            Optional<AiGeneratedImageKind> imageKind = parseImageKind(proposal.imageKind());
            if (country == null || imageKind.isEmpty()) {
                assessments.put(proposalIndex, rejected(
                        proposalIndex, imageKind.orElse(AiGeneratedImageKind.OTHER), null,
                        "Image proposal is missing a valid country or imageKind"));
                continue;
            }
            if (!isWikimediaUrl(proposal.imageSourceUrl())) {
                assessments.put(proposalIndex, rejected(
                        proposalIndex, imageKind.get(), null,
                        "Only a Wikimedia URL returned by an image search tool is allowed"));
                continue;
            }

            Optional<AiResolvedWikimediaImage> resolved = imageResolver.resolveImage(
                    proposal.imageSourceUrl(), proposal.searchTerms());
            if (resolved.isEmpty()) {
                assessments.put(proposalIndex, rejected(
                        proposalIndex, imageKind.get(), null,
                        "Wikimedia metadata or a commercially safe license could not be resolved"));
                continue;
            }
            if (imageKind.get() == AiGeneratedImageKind.CURRENCY
                    && !ImageRightsSupport.isPublicDomainOrCc0License(
                    resolved.get().licenseLabel(), resolved.get().licenseUrl())) {
                assessments.put(proposalIndex, rejected(
                        proposalIndex, imageKind.get(), resolved.get(),
                        "Currency image rejected: the file is not explicitly Public Domain or CC0"));
                continue;
            }

            candidates.add(new AiGenerationResolvedImageCandidate(
                    proposalIndex, proposal, country, imageKind.get(), resolved.get()));
        }

        if (candidates.isEmpty()) {
            return assessments;
        }

        List<Content> promptContents = buildPromptContents(candidates);
        try {
            Result<String> result = modelGateway.validateImages(
                    run,
                    stage,
                    requestIndex,
                    promptContents,
                    dispatchedInputTokens,
                    dispatchedOutputTokens,
                    dispatchedToolCalls);
            AiGenerationImageValidationBatchResult batchValidation =
                    structuredOutputParser.parseGenerationImageValidationResult(result);
            Map<Integer, AiGenerationImageValidationItemResult> resultsByIndex = safeItems(batchValidation).stream()
                    .filter(item -> item.proposalIndex() != null)
                    .collect(Collectors.toMap(
                            AiGenerationImageValidationItemResult::proposalIndex,
                            Function.identity(),
                            (left, right) -> right));

            candidates.forEach(candidate -> assessments.put(
                    candidate.proposalIndex(),
                    assess(candidate, resultsByIndex.get(candidate.proposalIndex()))));
        } catch (RuntimeException ex) {
            log.warn(
                    "AI generation image validation failed; image proposals will be rejected: runId={}, stageId={}, error={}",
                    run.id(),
                    stage.id(),
                    stageExecutionSupport.summarizeException(ex));
            candidates.forEach(candidate -> assessments.put(
                    candidate.proposalIndex(),
                    rejected(
                            candidate.proposalIndex(),
                            candidate.imageKind(),
                            candidate.resolvedImage(),
                            "Gemini visual validation was unavailable")));
        }

        return assessments;
    }

    private List<Content> buildPromptContents(List<AiGenerationResolvedImageCandidate> candidates) {
        List<Map<String, Object>> metadata = candidates.stream().map(candidate -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("proposalIndex", candidate.proposalIndex());
            item.put("countryName", candidate.country().countryName());
            item.put("countryCode", candidate.country().countryCode());
            item.put("declaredKind", candidate.imageKind().name());
            item.put("hintText", candidate.proposal().hintText());
            item.put("wikimediaTitle", candidate.resolvedImage().title());
            item.put("license", candidate.resolvedImage().licenseLabel());
            return item;
        }).toList();

        List<Content> contents = new ArrayList<>();
        contents.add(TextContent.from("Candidate metadata:\n" + stageExecutionSupport.toJson(metadata)));
        candidates.forEach(candidate -> {
            contents.add(TextContent.from(
                    "IMAGE FOR proposalIndex=" + candidate.proposalIndex()
                            + ", expectedCountry=" + candidate.country().countryName()
                            + ", declaredKind=" + candidate.imageKind().name()));
            contents.add(ImageContent.from(
                    URI.create(candidate.resolvedImage().imageUrl()),
                    ImageContent.DetailLevel.HIGH));
        });
        return contents;
    }

    private AiGenerationImageAssessment assess(
            AiGenerationResolvedImageCandidate candidate,
            AiGenerationImageValidationItemResult result) {
        if (result == null) {
            return rejected(
                    candidate.proposalIndex(), candidate.imageKind(), candidate.resolvedImage(),
                    "Gemini did not return a visual assessment for this image");
        }

        boolean validated = imagePolicy.isVisuallyApproved(candidate.imageKind(), result);
        String summary = stageExecutionSupport.firstNonBlank(
                result.validationSummary(),
                validated ? "Gemini visually approved the image clue" : "Gemini rejected the image clue");

        return new AiGenerationImageAssessment(
                candidate.proposalIndex(),
                candidate.imageKind(),
                candidate.resolvedImage(),
                validated,
                imagePolicy.normalize(result.answerTextVisibility()),
                imagePolicy.normalize(result.answerSymbolVisibility()),
                stageExecutionSupport.blankToNull(result.visualDescription()),
                summary);
    }

    private AiGenerationImageAssessment rejected(
            int proposalIndex,
            AiGeneratedImageKind imageKind,
            AiResolvedWikimediaImage resolvedImage,
            String summary) {
        return new AiGenerationImageAssessment(
                proposalIndex,
                imageKind,
                resolvedImage,
                false,
                null,
                null,
                null,
                summary);
    }

    private Optional<AiGeneratedImageKind> parseImageKind(String value) {
        if (!hasText(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(AiGeneratedImageKind.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private List<AiHintGenerationProposalResult> safeProposals(AiHintGenerationBatchResult result) {
        return result != null && result.proposals() != null ? result.proposals() : List.of();
    }

    private List<AiGenerationImageValidationItemResult> safeItems(
            AiGenerationImageValidationBatchResult result) {
        return result != null && result.items() != null ? result.items() : List.of();
    }

    private boolean isWikimediaUrl(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.startsWith("https://")
                && (normalized.contains("wikimedia.org/") || normalized.contains("wikipedia.org/"));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
