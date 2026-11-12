package io.clueterra.api.flaggame.service;

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
import io.clueterra.api.assets.service.FlagAssetPayload;
import io.clueterra.api.assets.service.FlagAssetService;
import io.clueterra.api.auth.repository.AuthJdbcRepository;
import io.clueterra.api.auth.repository.UserRow;
import io.clueterra.api.exception.LegalAcceptanceRequiredException;
import io.clueterra.api.flaggame.dto.FlagAnswerDto;
import io.clueterra.api.flaggame.dto.FlagClueDto;
import io.clueterra.api.flaggame.dto.FlagGuessResultDto;
import io.clueterra.api.flaggame.dto.FlagHintsDto;
import io.clueterra.api.flaggame.dto.FlagPlayerStatsDto;
import io.clueterra.api.flaggame.dto.FlagQuestionDto;
import io.clueterra.api.flaggame.dto.FlagRegionHintDto;
import io.clueterra.api.flaggame.dto.FlagRevealDto;
import io.clueterra.api.flaggame.dto.FlagSessionDto;
import io.clueterra.api.flaggame.model.FlagCountryRow;
import io.clueterra.api.flaggame.model.FlagGuessRow;
import io.clueterra.api.flaggame.model.FlagGuestStartResult;
import io.clueterra.api.flaggame.model.FlagHintRow;
import io.clueterra.api.flaggame.model.FlagPlayerSessionRow;
import io.clueterra.api.flaggame.model.FlagQuestionRow;
import io.clueterra.api.flaggame.repository.FlagGameJdbcRepository;
import io.clueterra.api.legal.service.LegalAcceptanceService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FlagGameService {

    private static final String FLAG_URL = "/api/flag-game/rounds/%d/flag.svg";
    private static final String REGION = "REGION";
    private static final String INITIAL = "INITIAL";
    private static final String CAPITAL = "CAPITAL";
    private static final int PERCENT = 100;

    private final FlagGameJdbcRepository repository;
    private final AuthJdbcRepository authRepository;
    private final LegalAcceptanceService legalAcceptanceService;
    private final TemplateBadgeEngine badgeEngine;
    private final FlagAssetService flagAssetService;
    private final FlagSvgAnonymizer anonymizer;
    private final ObjectMapper mapper;

    @Transactional
    public FlagGuestStartResult startGuest(String guestToken, String termsVersion, String privacyVersion) {
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
        return new FlagGuestStartResult(guest.guestToken(), sessionOf(guest.id()));
    }

    @Transactional
    public FlagSessionDto startRegistered(long userId) {
        ensureCurrentLegalAcceptance(userId);
        repository.selectNext(userId);
        return sessionOf(userId);
    }

    @Transactional
    public FlagSessionDto next(long userId) {
        ensureCurrentLegalAcceptance(userId);
        repository.selectNext(userId);
        return sessionOf(userId);
    }

    @Transactional(readOnly = true)
    public FlagSessionDto session(Long userId) {
        return Optional.ofNullable(userId)
                .map(this::sessionOf)
                .orElseGet(() -> new FlagSessionDto(
                        new FlagPlayerStatsDto(0, 0, repository.countFlags(), 0, 0, 0), null, List.of()));
    }

    @Transactional(readOnly = true)
    public FlagAssetPayload roundFlag(long userId, long round) {
        String iso2 = repository.findRoundFlag(userId, round)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flag is no longer current"));
        FlagAssetPayload flag = flagAssetService.fetchFlag(iso2);
        return new FlagAssetPayload(anonymizer.anonymize(flag.bytes()), flag.contentType());
    }

    @Transactional
    public FlagQuestionDto revealHint(long userId, long round) {
        ensureCurrentLegalAcceptance(userId);
        repository.revealHint(userId, round);
        return repository.findCurrent(userId)
                .map(row -> toDto(userId, row))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Flag changed; resume the game"));
    }

    @Transactional
    public FlagGuessResultDto guess(long userId, long round, String countryCode) {
        ensureCurrentLegalAcceptance(userId);
        FlagGuessRow result = repository.guess(userId, round, countryCode.toUpperCase(Locale.ROOT));
        if (result.correct()) {
            badgeEngine.evaluateForUser(userId, null);
        }
        return new FlagGuessResultDto(result.correct(), result.attempts(), result.xpAwarded(),
                result.correct() ? reveal(result.questionId()) : null, repository.findRoundMisses(userId, round),
                repository.findPlayerStats(userId));
    }

    @Transactional
    public FlagAnswerDto showAnswer(long userId, long round) {
        ensureCurrentLegalAcceptance(userId);
        long questionId = repository.showAnswer(userId, round);
        return new FlagAnswerDto(reveal(questionId), repository.findRoundMisses(userId, round),
                repository.findPlayerStats(userId));
    }

    @Transactional
    public FlagSessionDto skip(long userId, long round) {
        ensureCurrentLegalAcceptance(userId);
        repository.skip(userId, round);
        return sessionOf(userId);
    }

    @Transactional(readOnly = true)
    public FlagRevealDto discoveredCountry(Long userId, String countryCode) {
        return Optional.ofNullable(userId)
                .flatMap(id -> repository.findDiscoveredCountry(id, countryCode.toUpperCase(Locale.ROOT)))
                .map(this::toReveal)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Guess this flag first"));
    }

    private FlagSessionDto sessionOf(long userId) {
        FlagPlayerSessionRow player = repository.findPlayerSession(userId);
        return new FlagSessionDto(player.stats(),
                repository.findCurrent(userId).map(row -> toDto(userId, row)).orElse(null), player.solved());
    }

    private FlagRevealDto reveal(long questionId) {
        return repository.findCountry(questionId)
                .map(this::toReveal)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flag is unavailable"));
    }

    private FlagRevealDto toReveal(FlagCountryRow row) {
        return new FlagRevealDto(row.iso2(), row.region(), row.subregion(), row.names(), row.capitals());
    }

    private FlagQuestionDto toDto(long userId, FlagQuestionRow row) {
        List<FlagHintRow> clues = hints(row.hints());
        int hintsUsed = row.hintsUsed();
        Map<String, JsonNode> open = clues.stream()
                .filter(clue -> clue.position() <= hintsUsed)
                .collect(Collectors.toMap(FlagHintRow::kind, FlagHintRow::payload));
        FlagHintsDto hints = new FlagHintsDto(
                Optional.ofNullable(open.get(REGION)).map(payload -> new FlagRegionHintDto(
                        payload.path("region").asText(), payload.path("subregion").asText())).orElse(null),
                Optional.ofNullable(open.get(INITIAL)).map(payload -> payload.path("letters").toString()).orElse(null),
                Optional.ofNullable(open.get(CAPITAL)).map(payload -> payload.path("capitals").toString()).orElse(null));
        int baseXp = row.baseXp();
        IntUnaryOperator costXp = units -> baseXp * row.hintPenaltyPercent() * units / PERCENT;
        int spentUnits = clues.stream().filter(clue -> clue.position() <= hintsUsed).mapToInt(FlagHintRow::costUnits).sum();
        int maximumXp = row.alreadyRewarded() ? 0 : Math.max(0, baseXp - costXp.applyAsInt(spentUnits));
        List<FlagClueDto> clueCosts = clues.stream()
                .map(clue -> new FlagClueDto(clue.kind(), row.alreadyRewarded() ? 0 : costXp.applyAsInt(clue.costUnits())))
                .toList();
        return new FlagQuestionDto(row.round(), row.tier(), FLAG_URL.formatted(row.round()), hints, clueCosts, hintsUsed,
                row.attempts(), maximumXp, baseXp, repository.findRoundMisses(userId, row.round()));
    }

    private List<FlagHintRow> hints(String json) {
        try {
            return mapper.readValue(json,
                    mapper.getTypeFactory().constructCollectionType(List.class, FlagHintRow.class));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot decode flag clues", error);
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
