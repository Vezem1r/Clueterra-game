package io.clueterra.api.silhouettegame.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.achievements.engine.TemplateBadgeEngine;
import io.clueterra.api.auth.repository.AuthJdbcRepository;
import io.clueterra.api.auth.repository.UserRow;
import io.clueterra.api.exception.LegalAcceptanceRequiredException;
import io.clueterra.api.legal.service.LegalAcceptanceService;
import io.clueterra.api.silhouettegame.dto.SilhouetteAnswerDto;
import io.clueterra.api.silhouettegame.dto.SilhouetteClueDto;
import io.clueterra.api.silhouettegame.dto.SilhouetteGuessResultDto;
import io.clueterra.api.silhouettegame.dto.SilhouetteHintsDto;
import io.clueterra.api.silhouettegame.dto.SilhouetteLandmarkHintDto;
import io.clueterra.api.silhouettegame.dto.SilhouetteLocationHintDto;
import io.clueterra.api.silhouettegame.dto.SilhouetteMissDto;
import io.clueterra.api.silhouettegame.dto.SilhouettePhotoDto;
import io.clueterra.api.silhouettegame.dto.SilhouettePlayerStatsDto;
import io.clueterra.api.silhouettegame.dto.SilhouetteQuestionDto;
import io.clueterra.api.silhouettegame.dto.SilhouetteRegionHintDto;
import io.clueterra.api.silhouettegame.dto.SilhouetteRevealDto;
import io.clueterra.api.silhouettegame.dto.SilhouetteSessionDto;
import io.clueterra.api.silhouettegame.model.SilhouetteCountryRow;
import io.clueterra.api.silhouettegame.model.SilhouetteGuessRow;
import io.clueterra.api.silhouettegame.model.SilhouetteGuestStartResult;
import io.clueterra.api.silhouettegame.model.SilhouetteHintRow;
import io.clueterra.api.silhouettegame.model.SilhouettePhotoRow;
import io.clueterra.api.silhouettegame.model.SilhouettePlayerSessionRow;
import io.clueterra.api.silhouettegame.model.SilhouetteQuestionRow;
import io.clueterra.api.silhouettegame.repository.SilhouetteGameJdbcRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SilhouetteGameService {

    private static final String PHOTO_URL_PREFIX = "/api/assets/countries/photos/";
    private static final String PHOTO_KEY_PREFIX = "country-photos/";
    private static final String REGION = "REGION";
    private static final String LOCATION = "LOCATION";
    private static final String CAPITAL = "CAPITAL";
    private static final String FLAG_COLORS = "FLAG_COLORS";
    private static final String LANDMARK = "LANDMARK";
    private static final int PERCENT = 100;

    private final SilhouetteGameJdbcRepository repository;
    private final AuthJdbcRepository authRepository;
    private final LegalAcceptanceService legalAcceptanceService;
    private final TemplateBadgeEngine badgeEngine;
    private final ObjectMapper mapper;

    @Transactional
    public SilhouetteGuestStartResult startGuest(String guestToken, String termsVersion, String privacyVersion) {
        legalAcceptanceService.validateSubmittedVersions(termsVersion, privacyVersion);
        UserRow guest = Optional.ofNullable(guestToken)
                .filter(token -> !token.isBlank())
                .flatMap(authRepository::findByGuestToken)
                .orElseGet(() -> {
                    long userId = authRepository.insertGuestUser(termsVersion, privacyVersion);
                    return authRepository.findById(userId).orElseThrow();
                });
        authRepository.refreshGuestTokenExpiry(guest.id());
        if (!legalAcceptanceService.isCurrent(
                guest.termsVersionAccepted(), guest.privacyVersionAccepted())) {
            legalAcceptanceService.recordUserAcceptance(
                    guest.id(), termsVersion, privacyVersion,
                    LegalAcceptanceService.SOURCE_GUEST_REACCEPT);
        }
        repository.selectNext(guest.id());
        return new SilhouetteGuestStartResult(guest.guestToken(), sessionOf(guest.id()));
    }

    @Transactional
    public SilhouetteSessionDto startRegistered(long userId) {
        ensureCurrentLegalAcceptance(userId);
        repository.selectNext(userId);
        return sessionOf(userId);
    }

    @Transactional
    public SilhouetteSessionDto next(long userId) {
        ensureCurrentLegalAcceptance(userId);
        repository.selectNext(userId);
        return sessionOf(userId);
    }

    @Transactional(readOnly = true)
    public SilhouetteSessionDto session(Long userId) {
        return Optional.ofNullable(userId)
                .map(this::sessionOf)
                .orElseGet(() -> new SilhouetteSessionDto(
                        new SilhouettePlayerStatsDto(0, 0, repository.countCountries(), 0, 0, 0), null, List.of()));
    }

    @Transactional
    public SilhouetteQuestionDto revealHint(long userId, long round) {
        ensureCurrentLegalAcceptance(userId);
        repository.revealHint(userId, round);
        return repository.findCurrent(userId)
                .map(row -> toDto(userId, row))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Silhouette changed; resume the game"));
    }

    @Transactional
    public SilhouetteGuessResultDto guess(long userId, long round, String countryCode) {
        ensureCurrentLegalAcceptance(userId);
        String answer = countryCode.toUpperCase(Locale.ROOT);
        SilhouetteGuessRow result = repository.guess(userId, round, answer);
        if (result.correct()) {
            badgeEngine.evaluateForUser(userId, null);
        }
        List<SilhouetteMissDto> misses = repository.findRoundMisses(userId, round);
        SilhouetteMissDto miss = result.correct() ? null : misses.stream()
                .filter(candidate -> candidate.countryCode().equals(answer))
                .findFirst()
                .orElse(null);
        return new SilhouetteGuessResultDto(result.correct(), result.attempts(), result.xpAwarded(),
                result.correct() ? reveal(result.questionId()) : null, miss, misses,
                repository.findPlayerStats(userId));
    }

    @Transactional
    public SilhouetteAnswerDto showAnswer(long userId, long round) {
        ensureCurrentLegalAcceptance(userId);
        long questionId = repository.showAnswer(userId, round);
        return new SilhouetteAnswerDto(reveal(questionId), repository.findRoundMisses(userId, round),
                repository.findPlayerStats(userId));
    }

    @Transactional
    public SilhouetteSessionDto skip(long userId, long round) {
        ensureCurrentLegalAcceptance(userId);
        repository.skip(userId, round);
        return sessionOf(userId);
    }

    @Transactional(readOnly = true)
    public SilhouetteRevealDto discoveredCountry(Long userId, String countryCode) {
        return Optional.ofNullable(userId)
                .flatMap(id -> repository.findDiscoveredCountry(id, countryCode.toUpperCase(Locale.ROOT)))
                .map(this::toReveal)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Guess this country first"));
    }

    private SilhouetteSessionDto sessionOf(long userId) {
        SilhouettePlayerSessionRow player = repository.findPlayerSession(userId);
        return new SilhouetteSessionDto(player.stats(),
                repository.findCurrent(userId).map(row -> toDto(userId, row)).orElse(null), player.solved());
    }

    private SilhouetteRevealDto reveal(long questionId) {
        return repository.findCountry(questionId)
                .map(this::toReveal)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Country is unavailable"));
    }

    private SilhouetteRevealDto toReveal(SilhouetteCountryRow row) {
        SilhouettePhotoDto photo = Optional.ofNullable(row.photo())
                .map(found -> new SilhouettePhotoDto(photoUrl(found.objectKey()),
                        found.authorName(), found.licenseLabel(), found.licenseUrl(), found.sourcePageUrl(), found.landmarkNames()))
                .orElse(null);
        return new SilhouetteRevealDto(row.iso2(), row.subregion(), row.names(), row.facts(), row.flagColors(),
                List.of(row.centerLng(), row.centerLat()), row.bounds(), row.shapePath(), photo);
    }

    private SilhouetteQuestionDto toDto(long userId, SilhouetteQuestionRow row) {
        List<SilhouetteHintRow> clues = hints(row.hints());
        int hintsUsed = row.hintsUsed();
        Map<String, JsonNode> open = clues.stream()
                .filter(clue -> clue.position() <= hintsUsed)
                .collect(Collectors.toMap(SilhouetteHintRow::kind, SilhouetteHintRow::payload));
        SilhouetteHintsDto hints = new SilhouetteHintsDto(
                Optional.ofNullable(open.get(REGION)).map(payload -> new SilhouetteRegionHintDto(
                        payload.path("region").asText(), payload.path("subregion").asText(),
                        coordinates(payload.path("center")))).orElse(null),
                Optional.ofNullable(open.get(LOCATION)).map(payload -> new SilhouetteLocationHintDto(
                        coordinates(payload.path("center")), payload.path("bounds").toString())).orElse(null),
                Optional.ofNullable(open.get(CAPITAL)).map(payload -> payload.path("capitals").toString()).orElse(null),
                Optional.ofNullable(open.get(FLAG_COLORS)).map(payload -> payload.path("colors").toString()).orElse(null),
                open.containsKey(LANDMARK) ? landmark(row.photo()) : null);
        int baseXp = row.baseXp();
        IntUnaryOperator costXp = units -> baseXp * row.hintPenaltyPercent() * units / PERCENT;
        int spentUnits = clues.stream().filter(clue -> clue.position() <= hintsUsed).mapToInt(SilhouetteHintRow::costUnits).sum();
        int maximumXp = row.alreadyRewarded() ? 0 : Math.max(0, baseXp - costXp.applyAsInt(spentUnits));
        List<SilhouetteClueDto> clueCosts = clues.stream()
                .map(clue -> new SilhouetteClueDto(FLAG_COLORS.equals(clue.kind()) ? CAPITAL : clue.kind(),
                        row.alreadyRewarded() ? 0 : costXp.applyAsInt(clue.costUnits())))
                .toList();
        return new SilhouetteQuestionDto(row.round(), row.tier(), row.shapePath(), hints, clueCosts, hintsUsed, row.attempts(),
                maximumXp, baseXp, repository.findRoundMisses(userId, row.round()));
    }

    private SilhouetteLandmarkHintDto landmark(SilhouettePhotoRow photo) {
        return Optional.ofNullable(photo)
                .map(found -> new SilhouetteLandmarkHintDto(photoUrl(found.objectKey()), found.authorName(),
                        found.licenseLabel(), found.licenseUrl()))
                .orElse(null);
    }

    private String photoUrl(String objectKey) {
        return PHOTO_URL_PREFIX + objectKey.substring(PHOTO_KEY_PREFIX.length());
    }

    private List<Double> coordinates(JsonNode node) {
        return List.of(node.path(0).asDouble(), node.path(1).asDouble());
    }

    private List<SilhouetteHintRow> hints(String json) {
        try {
            return mapper.readValue(json,
                    mapper.getTypeFactory().constructCollectionType(List.class, SilhouetteHintRow.class));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot decode silhouette clues", error);
        }
    }

    private void ensureCurrentLegalAcceptance(long userId) {
        UserRow user = authRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player is unavailable"));
        if (!legalAcceptanceService.isCurrent(user.termsVersionAccepted(), user.privacyVersionAccepted())) {
            throw new LegalAcceptanceRequiredException();
        }
    }
}
